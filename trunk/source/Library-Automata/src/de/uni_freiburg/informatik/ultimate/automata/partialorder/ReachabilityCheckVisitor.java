/*
 * Copyright (C) 2021 Dominik Klumpp (klumpp@informatik.uni-freiburg.de)
 * Copyright (C) 2021 University of Freiburg
 *
 * This file is part of the ULTIMATE Automata Library.
 *
 * The ULTIMATE Automata Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE Automata Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Automata Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Automata Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Automata Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.automata.partialorder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/**
 * A visitor that checks reachability of a given set of states, and aborts the search as soon as such a state is
 * reached. At this point, it also adds all states on the stack to the given set of states.
 *
 * @author Dominik Klumpp (klumpp@informatik.uni-freiburg.de)
 *
 * @param <L>
 * @param <S>
 * @param <V>
 */
// TODO This duplicates in part code of AcceptingRunSearchVisitor, and is sort of an akward API. See if we can improve.
public class ReachabilityCheckVisitor<L, S, V extends IDfsVisitor<L, S>> extends WrapperVisitor<L, S, V> {

	private final Set<S> mCanReach;
	private boolean mFound;

	// The current stack of states
	private final Deque<S> mStateStack = new ArrayDeque<>();

	// A possible successor of the last state on the stack, which may become the next element on the stack.
	private S mPendingState;

	public ReachabilityCheckVisitor(final V underlying, final Set<S> canReach) {
		super(underlying);
		mCanReach = canReach;
	}

	@Override
	public boolean addStartState(final S state) {
		assert mStateStack.isEmpty() : "start state must be first";
		mStateStack.addLast(state);
		checkState(state);
		return mUnderlying.addStartState(state);
	}

	@Override
	public boolean discoverTransition(final S source, final L letter, final S target) {
		assert !mFound : "Unexpected transition discovery after abort";
		assert mStateStack.getLast() == source : "Unexpected transition from state " + source;
		mPendingState = target;
		return mUnderlying.discoverTransition(source, letter, target);
	}

	@Override
	public boolean discoverState(final S state) {
		assert !mFound : "Unexpected state discovery after abort";

		if (mPendingState == null) {
			// Must be initial state
			assert mStateStack.size() == 1 && mStateStack.getLast() == state : "Unexpected discovery of state " + state;
		} else {
			// Pending transition must lead to given state
			assert mPendingState == state : "Unexpected discovery of state " + state;
			mStateStack.addLast(mPendingState);
			mPendingState = null;
		}

		checkState(state);
		return mUnderlying.discoverState(state);
	}

	@Override
	public void backtrackState(final S state, final boolean isComplete) {
		assert !mFound : "Unexpected backtrack after abort";
		assert mStateStack.getLast() == state : "Unexpected backtrack of state " + state;

		mPendingState = null;
		mStateStack.removeLast();

		mUnderlying.backtrackState(state, isComplete);
	}

	@Override
	public boolean isFinished() {
		return mFound || mUnderlying.isFinished();
	}

	public boolean reachabilityConfirmed() {
		return mFound;
	}

	private void checkState(final S state) {
		assert !mFound : "Unexpected call after abort";
		assert mStateStack.getLast() == state : "Checked state is expected to be on top of stack";

		mFound = mCanReach.contains(state);
		if (mFound) {
			mCanReach.addAll(mStateStack);
		}
	}
}