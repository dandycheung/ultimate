/*
 * Copyright (C) 2021 Dominik Klumpp (klumpp@informatik.uni-freiburg.de)
 * Copyright (C) 2021 University of Freiburg
 *
 * This file is part of the ULTIMATE TraceAbstraction plug-in.
 *
 * The ULTIMATE TraceAbstraction plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE TraceAbstraction plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TraceAbstraction plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TraceAbstraction plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE TraceAbstraction plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.concurrency;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import de.uni_freiburg.informatik.ultimate.automata.nestedword.INwaOutgoingLetterAndTransitionProvider;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWordAutomataUtils;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.VpAlphabet;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingReturnTransition;
import de.uni_freiburg.informatik.ultimate.automata.partialorder.IDfsOrder;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IStateFactory;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.IcfgUtils;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.variables.IProgramConst;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.variables.IProgramFunction;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.IMLPredicate;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.concurrency.BetterLockstepOrder.RoundRobinComparator;

/*
 * Idea:
 *
 * - keep the order constant (preferring thread t)
 * - except: if I currently prefer thread t, and I see a transition of t that reaches a loop head, switch to t+1
 * - unclear: what if I prefer t, but I see a transition of t' reaching a loop head? keep t? or go to t'+1 ?
 *
 * For n threads of form "init; (loop)*; finish", the minimal traces under full commutativity should be
 * "init1,...,init_n,(loop1,...,loop_n)*,finish1,...,finish_n" (if all threads loop the same number of times)
 */
public class LoopLockstepOrder<L extends IIcfgTransition<?>> implements IDfsOrder<L, IPredicate> {

	private final Comparator<L> mDefaultComparator =
			Comparator.comparing(L::getPrecedingProcedure).thenComparingInt(Object::hashCode);
	private final IIcfg<?> mIcfg;
	private final Function<IPredicate, IPredicate> mNormalize;

	public LoopLockstepOrder(final IIcfg<?> icfg, final Function<IPredicate, IPredicate> normalize) {
		mIcfg = icfg;
		mNormalize = normalize;
	}

	@Override
	public Comparator<L> getOrder(final IPredicate state) {
		final IPredicate original = mNormalize == null ? state : mNormalize.apply(state);
		if (original instanceof PredicateWithLastThread) {
			return new RoundRobinComparator<>(((PredicateWithLastThread) original).getLastThread(), mDefaultComparator);
		}
		throw new IllegalArgumentException("Expected PredicateWithLastThread, got " + original);
	}

	@Override
	public boolean isPositional() {
		return true;
	}

	public INwaOutgoingLetterAndTransitionProvider<L, IPredicate>
			wrapAutomaton(final INwaOutgoingLetterAndTransitionProvider<L, IPredicate> automaton) {
		assert NestedWordAutomataUtils.isFiniteAutomaton(automaton) : "No calls and returns supported";
		final Optional<String> maxThread =
				IcfgUtils.getAllThreadInstances(mIcfg).stream().min(Comparator.naturalOrder());
		assert maxThread.isPresent() : "No thread found";
		return new WrapperAutomaton<>(automaton, maxThread.get(), mIcfg.getLoopLocations());
	}

	private static final class WrapperAutomaton<L extends IIcfgTransition<?>>
			implements INwaOutgoingLetterAndTransitionProvider<L, IPredicate> {
		private final INwaOutgoingLetterAndTransitionProvider<L, IPredicate> mUnderlying;
		private final String mMaxThread;
		private final Set<? extends IcfgLocation> mLoopHeads;

		private final Map<IPredicate, Map<String, IPredicate>> mKnownStates = new HashMap<>();

		private WrapperAutomaton(final INwaOutgoingLetterAndTransitionProvider<L, IPredicate> automaton,
				final String maxThread, final Set<? extends IcfgLocation> loopHeads) {
			mUnderlying = automaton;
			mMaxThread = maxThread;
			mLoopHeads = loopHeads;
		}

		@Override
		public IStateFactory<IPredicate> getStateFactory() {
			throw new UnsupportedOperationException();
		}

		@Override
		public VpAlphabet<L> getVpAlphabet() {
			return mUnderlying.getVpAlphabet();
		}

		@Override
		public IPredicate getEmptyStackState() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Iterable<IPredicate> getInitialStates() {
			return StreamSupport.stream(mUnderlying.getInitialStates().spliterator(), false)
					.map(q -> getOrCreateState(q, mMaxThread)).collect(Collectors.toSet());
		}

		@Override
		public boolean isInitial(final IPredicate state) {
			if (state instanceof PredicateWithLastThread) {
				return mUnderlying.isFinal(((PredicateWithLastThread) state).getUnderlying())
						&& mMaxThread.equals(((PredicateWithLastThread) state).getLastThread());
			}
			throw new IllegalArgumentException();
		}

		@Override
		public boolean isFinal(final IPredicate state) {
			if (state instanceof PredicateWithLastThread) {
				return mUnderlying.isFinal(((PredicateWithLastThread) state).getUnderlying());
			}
			throw new IllegalArgumentException();
		}

		@Override
		public int size() {
			return -1;
		}

		@Override
		public String sizeInformation() {
			return "<unknown>";
		}

		@Override
		public Set<L> lettersInternal(final IPredicate state) {
			if (state instanceof PredicateWithLastThread) {
				return mUnderlying.lettersInternal(((PredicateWithLastThread) state).getUnderlying());
			}
			throw new IllegalArgumentException();
		}

		@Override
		public Iterable<OutgoingInternalTransition<L, IPredicate>> internalSuccessors(final IPredicate state,
				final L letter) {
			if (!(state instanceof PredicateWithLastThread)) {
				throw new IllegalArgumentException();
			}
			final PredicateWithLastThread predState = (PredicateWithLastThread) state;

			// Keep the same order between threads, until we reach a loop head. Then we shift the order by one thread.
			// This allows other threads to interrupt before we enter the loop, and after every iteration.
			final String lastThread;
			final IcfgLocation target = letter.getTarget();
			if (mLoopHeads.contains(target)) {
				lastThread = target.getProcedure();
			} else {
				lastThread = predState.getLastThread();
			}

			return StreamSupport
					.stream(mUnderlying.internalSuccessors(predState.getUnderlying(), letter).spliterator(), false)
					.map(outgoing -> new OutgoingInternalTransition<>(letter,
							getOrCreateState(outgoing.getSucc(), lastThread)))
					.collect(Collectors.toSet());
		}

		@Override
		public Iterable<OutgoingCallTransition<L, IPredicate>> callSuccessors(final IPredicate state, final L letter) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Iterable<OutgoingReturnTransition<L, IPredicate>> returnSuccessors(final IPredicate state,
				final IPredicate hier, final L letter) {
			throw new UnsupportedOperationException();
		}

		private IPredicate getOrCreateState(final IPredicate underlying, final String lastThread) {
			final Map<String, IPredicate> thread2State = mKnownStates.computeIfAbsent(underlying, x -> new HashMap<>());
			return thread2State.computeIfAbsent(lastThread,
					x -> new PredicateWithLastThread((IMLPredicate) underlying, lastThread));
		}
	}

	public static final class PredicateWithLastThread implements IMLPredicate {
		private final IMLPredicate mUnderlying;

		private final String mLastThread;

		public PredicateWithLastThread(final IMLPredicate underlying, final String lastThread) {
			mUnderlying = underlying;
			mLastThread = lastThread;
		}

		public IMLPredicate getUnderlying() {
			return mUnderlying;
		}

		public String getLastThread() {
			return mLastThread;
		}

		@Override
		public IcfgLocation[] getProgramPoints() {
			return mUnderlying.getProgramPoints();
		}

		@Override
		public Term getFormula() {
			return mUnderlying.getFormula();
		}

		@Override
		public Term getClosedFormula() {
			return mUnderlying.getClosedFormula();
		}

		@Override
		public String[] getProcedures() {
			return mUnderlying.getProcedures();
		}

		@Override
		public Set<IProgramVar> getVars() {
			return mUnderlying.getVars();
		}

		@Override
		public Set<IProgramConst> getConstants() {
			return mUnderlying.getConstants();
		}

		@Override
		public Set<IProgramFunction> getFunctions() {
			return mUnderlying.getFunctions();
		}

		@Override
		public int hashCode() {
			return Objects.hash(mLastThread, mUnderlying);
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final PredicateWithLastThread other = (PredicateWithLastThread) obj;
			return Objects.equals(mLastThread, other.mLastThread) && Objects.equals(mUnderlying, other.mUnderlying);
		}
	}
}