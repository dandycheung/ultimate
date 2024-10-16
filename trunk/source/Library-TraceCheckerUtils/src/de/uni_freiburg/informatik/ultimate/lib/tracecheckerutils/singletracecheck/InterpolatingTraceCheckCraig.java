/*
 * Copyright (C) 2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE TraceCheckerUtils Library.
 *
 * The ULTIMATE TraceCheckerUtils Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE TraceCheckerUtils Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TraceCheckerUtils Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TraceCheckerUtils Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE TraceCheckerUtils Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.lib.tracecheckerutils.singletracecheck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWord;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.RunningTaskInfo;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.ToolchainCanceledException;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IAction;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IIcfgCallTransition;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.interpolant.InterpolantComputationStatus;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.interpolant.InterpolantComputationStatus.ItpErrorStatus;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.IPredicateUnifier;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.PredicateFactory;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.TermVarsFuns;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.tracecheck.ITraceCheckPreferences.AssertCodeBlockOrder;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.tracecheck.ITraceCheckPreferences.AssertCodeBlockOrderType;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.tracecheck.TraceCheckReasonUnknown;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.ManagedScript;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.lib.tracecheckerutils.singletracecheck.TraceCheckStatisticsGenerator.InterpolantType;
import de.uni_freiburg.informatik.ultimate.logic.SMTLIBException;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;

/**
 * Uses Craig interpolation for computation of nested interpolants. Supports two algorithms. 1. Matthias' recursive
 * algorithm. 2. Tree interpolation
 *
 * @author heizmann@informatik.uni-freiburg.de
 */
public class InterpolatingTraceCheckCraig<L extends IAction> extends InterpolatingTraceCheck<L> {

	private final boolean mInstantiateArrayExt;
	private final InterpolantComputationStatus mInterpolantComputationStatus;

	/**
	 * Check if trace fulfills specification given by precondition, postcondition and pending contexts. The
	 * pendingContext maps the positions of pending returns to predicates which define possible variable valuations in
	 * the context to which the return leads the trace.
	 */
	public InterpolatingTraceCheckCraig(final IPredicate precondition, final IPredicate postcondition,
			final SortedMap<Integer, IPredicate> pendingContexts, final NestedWord<L> trace,
			final List<? extends Object> controlLocationSequence, final IUltimateServiceProvider services,
			final CfgSmtToolkit csToolkit, final ManagedScript mgdScriptTc, final PredicateFactory predicateFactory,
			final IPredicateUnifier predicateUnifier, final AssertCodeBlockOrder assertCodeBlockOrder,
			final boolean computeRcfgProgramExecution, final boolean collectInterpolantStatistics,
			final InterpolationTechnique interpolation, final boolean instantiateArrayExt,
			final SimplificationTechnique simplificationTechnique, final boolean innerRecursiveNestedInterpolationCall) {
		super(precondition, postcondition, pendingContexts, trace, controlLocationSequence, services, csToolkit,
				mgdScriptTc, predicateFactory, predicateUnifier, assertCodeBlockOrder, computeRcfgProgramExecution,
				collectInterpolantStatistics, simplificationTechnique);
		if (assertCodeBlockOrder.getAssertCodeBlockOrderType() != AssertCodeBlockOrderType.NOT_INCREMENTALLY) {
			throw new UnsupportedOperationException("incremental assertion is not available for Craig interpolation");
		}
		mInstantiateArrayExt = instantiateArrayExt;
		if (isCorrect() == LBool.UNSAT) {
			InterpolantComputationStatus ics = new InterpolantComputationStatus();
			try {
				computeInterpolants(interpolation);
				mTraceCheckBenchmarkGenerator.reportSequenceOfInterpolants(Arrays.asList(mInterpolants),
						InterpolantType.Craig);
				if (!innerRecursiveNestedInterpolationCall) {
					mTraceCheckBenchmarkGenerator.reportInterpolantComputation();
					checkPerfectSequence(getIpp());
				}
			} catch (final UnsupportedOperationException e) {
				ics = handleUnsupportedOperationException(e);
			} catch (final SMTLIBException e) {
				ics = handleSmtLibException(e);
			} catch (final IllegalArgumentException e) {
				ics = handleIllegalArgumentException(e);
			} catch (final NestedTraceCheckException e) {
				ics = handleNestedTraceCheckException(e);
			}
			mTraceCheckFinished = true;
			mInterpolantComputationStatus = ics;
		} else if (isCorrect() == LBool.SAT) {
			mInterpolantComputationStatus = new InterpolantComputationStatus(ItpErrorStatus.TRACE_FEASIBLE, null);
		} else {
			mInterpolantComputationStatus =
					new InterpolantComputationStatus(ItpErrorStatus.SMT_SOLVER_CANNOT_INTERPOLATE_INPUT, null);
		}
	}

	public InterpolatingTraceCheckCraig(final IPredicate precondition, final IPredicate postcondition,
			final SortedMap<Integer, IPredicate> pendingContexts, final NestedWord<L> trace,
			final List<? extends Object> controlLocationSequence, final IUltimateServiceProvider services,
			final CfgSmtToolkit csToolkit, final PredicateFactory predicateFactory,
			final IPredicateUnifier predicateUnifier, final AssertCodeBlockOrder assertCodeBlockOrder,
			final boolean computeRcfgProgramExecution, final boolean collectInterpolantStatistics,
			final InterpolationTechnique interpolation, final boolean instantiateArrayExt,
			final SimplificationTechnique simplificationTechnique) {
		this(precondition, postcondition, pendingContexts, trace, controlLocationSequence, services, csToolkit,
				csToolkit.getManagedScript(), predicateFactory, predicateUnifier, assertCodeBlockOrder,
				computeRcfgProgramExecution, collectInterpolantStatistics, interpolation, instantiateArrayExt,
				simplificationTechnique, false);
	}

	private InterpolantComputationStatus handleNestedTraceCheckException(final NestedTraceCheckException e) {
		// unwrap nested exception and handle it here
		final Throwable cause = e.getCause();
		final InterpolantComputationStatus ics;
		if (cause instanceof UnsupportedOperationException) {
			ics = handleUnsupportedOperationException((UnsupportedOperationException) cause);
		} else if (cause instanceof SMTLIBException) {
			ics = handleSmtLibException((SMTLIBException) cause);
		} else if (cause instanceof IllegalArgumentException) {
			ics = handleIllegalArgumentException((IllegalArgumentException) cause);
		} else {
			throw e;
		}
		return ics;
	}

	private InterpolantComputationStatus handleUnsupportedOperationException(final UnsupportedOperationException e) {
		final String message = throwIfNoMessage(e);
		if (isMessageSolverCannotInterpolate(message)) {
			// SMTInterpol throws this during interpolation for unsupported fragments such as arrays
			return new InterpolantComputationStatus(ItpErrorStatus.SMT_SOLVER_CANNOT_INTERPOLATE_INPUT, e);
		}
		throw e;
	}

	private InterpolantComputationStatus handleSmtLibException(final SMTLIBException e) {
		if (!mServices.getProgressMonitorService().continueProcessing()) {
			// There was a cancellation request, probably responsible for abnormal solver termination.
			// Propagate it as a ToolchainCanceledException so appropriate timeout handling can take place.
			throw new ToolchainCanceledException(getClass(), "while computing interpolants");
		}

		final String message = throwIfNoMessage(e);
		if ("Unsupported non-linear arithmetic".equals(message)) {
			// SMTInterpol was somehow able to determine satisfiability but detects
			// non-linear arithmetic during interpolation
			return new InterpolantComputationStatus(ItpErrorStatus.SMT_SOLVER_CANNOT_INTERPOLATE_INPUT, e);
		}
		throw e;
	}

	private InterpolantComputationStatus handleIllegalArgumentException(final IllegalArgumentException e) {
		final String message = throwIfNoMessage(e);
		if (message.startsWith("Did not find overload for function =")) {
			// DD: this is a known bug in SMTInterpol; until it is fixed, we catch it here so that we can run
			// benchmarks
			return new InterpolantComputationStatus(ItpErrorStatus.SMT_SOLVER_CRASH, e);
		}
		throw e;
	}

	private String throwIfNoMessage(final RuntimeException e) {
		final String message = e.getMessage();
		if (message == null) {
			mLogger.fatal("Solver crashed with " + e.getClass().getSimpleName() + " whose message is null");
			throw e;
		}
		return message;
	}

	private static boolean isMessageSolverCannotInterpolate(final String message) {
		return message.startsWith("Cannot interpolate") || NestedInterpolantsBuilder.DIFF_IS_UNSUPPORTED.equals(message)
				|| message.startsWith("Unknown lemma type!")
				|| message.startsWith("Interpolation not supported for quantified formulae");
	}

	/**
	 *
	 * @param interpolation
	 * @return
	 */
	protected int getTotalNumberOfPredicates(final InterpolationTechnique interpolation) {
		return mInterpolants != null ? mInterpolants.length : 0;
	}

	@Override
	protected void computeInterpolants(final InterpolationTechnique interpolation) {
		mTraceCheckBenchmarkGenerator.start(TraceCheckStatisticsDefinitions.InterpolantComputationTime.toString());
		assert mPredicateUnifier != null;
		assert mPredicateUnifier.isRepresentative(mPrecondition);
		assert mPredicateUnifier.isRepresentative(mPostcondition);
		for (final IPredicate pred : mPendingContexts.values()) {
			assert mPredicateUnifier.isRepresentative(pred);
		}
		try {
			switch (interpolation) {
			case Craig_NestedInterpolation:
				computeInterpolantsRecursive();
				break;
			case Craig_TreeInterpolation:
				computeInterpolantsTree();
				break;
			default:
				throw new UnsupportedOperationException("unsupportedInterpolation");
			}
			mTraceCheckFinished = true;
		} catch (final ToolchainCanceledException tce) {
			final String taskDescription = "constructing Craig interpolants";
			tce.addRunningTaskInfo(new RunningTaskInfo(getClass(), taskDescription));
			throw tce;
		} finally {
			mTraceCheckBenchmarkGenerator.stop(TraceCheckStatisticsDefinitions.InterpolantComputationTime.toString());
		}
		// TODO: remove this if relevant variables are definitely correct.
		// assert testRelevantVars() : "bug in relevant variables";
	}

	private boolean testRelevantVars() {
		boolean result = true;
		final RelevantVariables rv = new RelevantVariables(mNestedFormulas, mCsToolkit.getModifiableGlobalsTable());
		for (int i = 0; i < mInterpolants.length; i++) {
			final IPredicate itp = mInterpolants[i];
			final Set<IProgramVar> vars = itp.getVars();
			final Set<IProgramVar> frel = rv.getForwardRelevantVariables()[i + 1];
			final Set<IProgramVar> brel = rv.getBackwardRelevantVariables()[i + 1];
			if (!frel.containsAll(vars)) {
				mLogger.warn("forward relevant variables wrong");
				result = false;
			}
			if (!brel.containsAll(vars)) {
				mLogger.warn("backward relevant variables wrong");
				result = false;
			}
		}
		return result;
	}

	@Override
	public IPredicate[] getInterpolants() {
		if (isCorrect() == LBool.UNSAT) {
			if (mInterpolants == null) {
				throw new AssertionError("No Interpolants");
			}
			assert mInterpolants.length == mTrace.length() - 1;
			return mInterpolants;
		}
		throw new UnsupportedOperationException("Interpolants are only available if trace is correct.");
	}

	@Override
	public InterpolantComputationStatus getInterpolantComputationStatus() {
		return mInterpolantComputationStatus;
	}

	/**
	 * Use tree interpolants to compute nested interpolants.
	 */
	private void computeInterpolantsTree() {
		if (mFeasibilityResult.getLBool() != LBool.UNSAT) {
			throw new IllegalArgumentException("Interpolants only available if trace fulfills specification");
		}
		if (mInterpolants != null) {
			throw new AssertionError("You already computed interpolants");
		}
		final Set<Integer> skippedInnerProcedurePositions = Collections.emptySet();
		final NestedInterpolantsBuilder<L> nib = new NestedInterpolantsBuilder<>(mTcSmtManager, mTraceCheckLock,
				mAAA.getAnnotatedSsa(), mNsb.getConstants2BoogieVar(), mPredicateUnifier, mPredicateFactory,
				skippedInnerProcedurePositions, true, mServices, this, mCfgManagedScript, mInstantiateArrayExt,
				mSimplificationTechnique, mPrecondition, mPostcondition);
		mInterpolants = nib.getNestedInterpolants();
		assert TraceCheckUtils.checkInterpolantsInductivityForward(Arrays.asList(mInterpolants), mTrace, mPrecondition,
				mPostcondition, mPendingContexts, "Craig", mCsToolkit,
				mLogger) : "invalid Hoare triple in tree interpolants";
		assert mInterpolants != null;
	}

	/**
	 * Use Matthias' old naive iterative method to compute nested interpolants. (Recursive interpolation queries, one
	 * for each call-return pair)
	 */
	private void computeInterpolantsRecursive() {
		if (mFeasibilityResult.getLBool() != LBool.UNSAT) {
			if (mFeasibilityResult.getLBool() == null) {
				throw new AssertionError("No trace check at the moment - no interpolants!");
			}
			throw new AssertionError("Interpolants only available if trace fulfills specification");
		}
		if (mInterpolants != null) {
			throw new AssertionError("You already computed interpolants");
		}

		final List<Integer> outerNonPendingCallPositions = computeOutermostNonPendingCallPosition(mTrace);
		final Set<Integer> skippedInnerProcedurePositions = computeSkippedInnerProcedurePositions(mTrace,
				outerNonPendingCallPositions);

		final NestedInterpolantsBuilder<L> nib = new NestedInterpolantsBuilder<>(mTcSmtManager, mTraceCheckLock,
				mAAA.getAnnotatedSsa(), mNsb.getConstants2BoogieVar(), mPredicateUnifier, mPredicateFactory,
				skippedInnerProcedurePositions, false, mServices, this, mCfgManagedScript, mInstantiateArrayExt,
				mSimplificationTechnique, mPrecondition, mPostcondition);
		mInterpolants = nib.getNestedInterpolants();
		final IPredicate oldPrecondition = mPrecondition;
		final IPredicate oldPostcondition = mPostcondition;

		for (final Integer nonPendingCall : outerNonPendingCallPositions) {
			// compute subtrace from to call to corresponding return
			final int returnPosition = mTrace.getReturnPosition(nonPendingCall);
			final NestedWord<L> subtrace = mTrace.getSubWord(nonPendingCall + 1, returnPosition + 1);

			final IIcfgCallTransition<?> call = (IIcfgCallTransition<?>) mTrace.getSymbol(nonPendingCall);
			final String calledMethod = call.getSucceedingProcedure();
			final TermVarsFuns oldVarsEquality = TraceCheckUtils.getOldVarsEquality(calledMethod,
					mCsToolkit.getModifiableGlobalsTable(), mCfgManagedScript);

			final IPredicate precondition = mPredicateUnifier.getOrConstructPredicate(oldVarsEquality.getFormula());

			// Use a pendingContext the interpolant at the position before the
			// call, if this is -1 (because call is first codeBlock) use the
			// precondition used in this recursive interpolant computation one
			// level above
			final SortedMap<Integer, IPredicate> pendingContexts = new TreeMap<>();
			IPredicate beforeCall;
			if (nonPendingCall == 0) {
				beforeCall = oldPrecondition;
			} else {
				beforeCall = mInterpolants[nonPendingCall - 1];
			}
			pendingContexts.put(subtrace.length() - 1, beforeCall);

			// Check if subtrace is "compatible" with interpolants computed so
			// far. Obviously trace fulfills specification, but we need this
			// proof to be able to compute interpolants.
			IPredicate interpolantAtReturnPosition;
			if (returnPosition == mTrace.length() - 1) {
				// special case: last position of trace is return
				// interpolant at this position is the postcondition
				// (which is stored in oldPostcondition, since mPostcondition
				// is already set to null.
				interpolantAtReturnPosition = oldPostcondition;
			} else {
				interpolantAtReturnPosition = mInterpolants[returnPosition];
			}
			assert interpolantAtReturnPosition != null;

			mLogger.info("Compute interpolants for subsequence at non-pending call position " + nonPendingCall);
			// Compute interpolants for subsequence and add them to interpolants
			// computed by this traceCheck
			final InterpolatingTraceCheckCraig<L> tc = new InterpolatingTraceCheckCraig<>(precondition,
					interpolantAtReturnPosition, pendingContexts, subtrace, null, mServices, mCsToolkit, mTcSmtManager,
					mPredicateFactory, mPredicateUnifier, mAssertCodeBlockOrder, false,
					mTraceCheckBenchmarkGenerator.isCollectingInterpolantSequenceStatistics(),
					InterpolationTechnique.Craig_NestedInterpolation, mInstantiateArrayExt, mSimplificationTechnique,
					true);
			final LBool isSafe = tc.isCorrect();
			if (isSafe == LBool.SAT) {
				throw new AssertionError(
						"has to be unsat by construction, we do check only for interpolant computation");
			}
			if (isSafe == LBool.UNKNOWN) {
				if (!mServices.getProgressMonitorService().continueProcessing()) {
					throw new ToolchainCanceledException(this.getClass(), "construction of nested interpolants");

				}
				final TraceCheckReasonUnknown reasonsUnknown = tc.getTraceCheckReasonUnknown();
				throw new NestedTraceCheckException("UNKNOWN during nested interpolation. I don't know how to continue",
						reasonsUnknown.getException());
			}
			// tc.computeInterpolants_Recursive(interpolatedPositions, mPredicateUnifier);
			final IPredicate[] interpolantSubsequence = tc.getInterpolants();

			assert mPredicateFactory.isDontCare(mInterpolants[nonPendingCall]);
			mInterpolants[nonPendingCall] = precondition;
			for (int i = 0; i < interpolantSubsequence.length; i++) {
				assert mPredicateFactory.isDontCare(mInterpolants[nonPendingCall + 1 + i]);
				mInterpolants[nonPendingCall + 1 + i] = interpolantSubsequence[i];
			}
		}

		assert TraceCheckUtils.checkInterpolantsInductivityForward(Arrays.asList(mInterpolants), mTrace, mPrecondition,
				mPostcondition, mPendingContexts, "Craig", mCsToolkit,
				mLogger) : "invalid Hoare triple in nested interpolants";
	}

	private static <L> List<Integer> computeOutermostNonPendingCallPosition(final NestedWord<L> trace) {
		final List<Integer> result = new ArrayList<>();
		int i = 0;
		while (i < trace.length()) {
			// if i is position of non-pending call then set i to
			// the position of the return and increment it afterwards
			if (trace.isCallPosition(i) && !trace.isPendingCall(i)) {
				result.add(i);
				i = trace.getReturnPosition(i);
			}
			i++;
		}
		return result;
	}

	/**
	 * Positions where we want to omit the computation of interpolants because we
	 * compute the interpolant later in a recursive interpolation call. We include
	 * the position of the call (no interpolant after the call) and exclude the
	 * position of the return (we want interpolant directly after return).
	 */
	private static <L> Set<Integer> computeSkippedInnerProcedurePositions(final NestedWord<L> trace,
			final List<Integer> nonPendingCalls) {
		final Set<Integer> result = new HashSet<>();
		for (final int callPos : nonPendingCalls) {
			final int returnPos = trace.getReturnPosition(callPos);
			for (int i = callPos; i < returnPos; i++) {
				result.add(i);
			}
		}
		return result;
	}


	/**
	 * A {@link RuntimeException} that can be thrown when a nested trace check fails.
	 *
	 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
	 *
	 */
	private static final class NestedTraceCheckException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public NestedTraceCheckException(final String message, final Throwable cause) {
			super(message, cause);
		}
	}

}
