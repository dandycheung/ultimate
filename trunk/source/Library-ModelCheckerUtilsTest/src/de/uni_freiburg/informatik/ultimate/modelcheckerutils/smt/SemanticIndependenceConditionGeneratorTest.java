/*
 * Copyright (C) 2021 Dominik Klumpp (klumpp@informatik.uni-freiburg.de)
 * Copyright (C) 2021 University of Freiburg
 *
 * This file is part of the ULTIMATE ModelCheckerUtilsTest Library.
 *
 * The ULTIMATE ModelCheckerUtilsTest Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE ModelCheckerUtilsTest Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ModelCheckerUtilsTest Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ModelCheckerUtilsTest Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE ModelCheckerUtilsTest Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger.LogLevel;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.DefaultIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.TransFormulaBuilder;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.TransFormulaUtils;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.variables.ProgramVarUtils;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.independence.SemanticIndependenceConditionGenerator;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.BasicPredicateFactory;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.scripttransfer.HistoryRecordingScript;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.CommuhashNormalForm;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.ManagedScript;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.logic.Logics;
import de.uni_freiburg.informatik.ultimate.logic.QuantifiedFormula;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.smtsolver.external.TermParseUtils;
import de.uni_freiburg.informatik.ultimate.test.mocks.UltimateMocks;

/**
 * Tests for the {@link SemanticIndependenceConditionGenerator}. Examples are taken from the paper
 *
 * "Decomposing Data Structure Commutativity Proofs with mn-Differencing" by Koskinen and Bansal, VMCAI 2021
 *
 * Although the goal there is different, the examples still can be used in our context.
 *
 * @author Dominik Klumpp (klumpp@informatik.uni-freiburg.de)
 */
public class SemanticIndependenceConditionGeneratorTest {
	private static final long TEST_TIMEOUT_MILLISECONDS = 10_000;
	private static final LogLevel LOG_LEVEL = LogLevel.INFO;
	private static final String SOLVER_COMMAND = "z3 SMTLIB2_COMPLIANT=true -t:1000 -memory:2024 -smt2 -in";

	private IUltimateServiceProvider mServices;
	private ILogger mLogger;
	private Script mScript;
	private ManagedScript mMgdScript;
	private final DefaultIcfgSymbolTable mSymbolTable = new DefaultIcfgSymbolTable();
	private SemanticIndependenceConditionGenerator mGenerator;

	// variables for SimpleSet example
	private IProgramVar x, y, a, b, sz, r1, r2, s1, s2;

	// variables for ArrayStack example
	private IProgramVar arr, max, top, e1, e2;

	private Term axioms;

	@Before
	public void setUp() {
		mServices = UltimateMocks.createUltimateServiceProviderMock(LOG_LEVEL);
		mServices.getProgressMonitorService().setDeadline(System.currentTimeMillis() + TEST_TIMEOUT_MILLISECONDS);
		mLogger = mServices.getLoggingService().getLogger(SemanticIndependenceConditionGeneratorTest.class);

		mScript = new HistoryRecordingScript(UltimateMocks.createSolver(SOLVER_COMMAND, LOG_LEVEL));
		mMgdScript = new ManagedScript(mServices, mScript);
		mScript.setLogic(Logics.ALL);

		axioms = mScript.term("true");
		setupSimpleSet();
		setupArrayStack();

		mGenerator = new SemanticIndependenceConditionGenerator(mServices, mMgdScript,
				new BasicPredicateFactory(mServices, mMgdScript, mSymbolTable), false);
	}

	@After
	public void tearDown() {
		mScript.exit();
	}

	@Test
	public void isInIsIn() {
		runTest(isIn(x, r1), isIn(y, r2), mScript.term("true"));
	}

	@Test
	public void addIsIn() {
		final Term expected =
				parseWithVariables("(or (distinct x y) (= a x) (= b x) (and (distinct a (- 1)) (distinct b (- 1))))");
		runTest(add(x), isIn(y, r1), expected);
	}

	@Test
	public void clearIsIn() {
		final Term expected = parseWithVariables("(and (distinct a x) (distinct b x))");
		runTest(clear(), isIn(x, r1), expected);
	}

	@Test
	public void getSizeIsIn() {
		runTest(getSize(s1), isIn(x, r1), mScript.term("true"));
	}

	@Test
	public void clearAdd() {
		runTest(clear(), add(x), null);
	}

	@Test
	public void getSizeClear() {
		final Term expected = parseWithVariables("(= sz 0)");
		runTest(getSize(s1), clear(), expected);
	}

	@Test
	public void getSizeAdd() {
		final Term expected = parseWithVariables("(or (= a x) (= b x) (and (distinct a (- 1)) (distinct b (- 1))))");
		runTest(getSize(s1), add(x), expected);
	}

	@Test
	public void popPop() {
		final Term expected = parseWithVariables("(= top (- 1))");
		runTest(pop(s1), pop(s2), expected);
	}

	@Test
	public void popPush() {
		// Note: The commutativity condition (and (> top (- 1)) (= (select arr top) e1) (< top max)) from the paper
		// only guarantees equivalence "with respect to stack semantics", i.e., observational equivalence.
		// Instead, we find the trivial case of a full 0-capacity stack, which guarantees our notion of commutativity.
		final Term expected = parseWithVariables("(and (= top (- max 1)) (= max 0))");
		runTest(pop(s1), push(e1, r1), expected);
	}

	@Test
	public void popIsEmpty() {
		final Term expected = parseWithVariables("(distinct top 0)");
		runTest(pop(s1), isEmpty(r1), expected);
	}

	@Test
	public void pushPush() {
		final Term expected = parseWithVariables("(= (+ top 1) max)");
		runTest(push(e1, r1), push(e2, r2), expected);
	}

	@Test
	public void pushIsEmpty() {
		final Term expected = parseWithVariables("(distinct top (- 1))");
		runTest(push(e1, r2), isEmpty(r1), expected);
	}

	@Test
	public void isEmptyIsEmpty() {
		runTest(isEmpty(r1), isEmpty(r2), mScript.term("true"));
	}

	private void runTest(final UnmodifiableTransFormula tfA, final UnmodifiableTransFormula tfB, final Term expected) {
		// TODO Actually run tests against SemanticIndependenceRelation: Check if result is sufficient for independence.

		final IPredicate actual = mGenerator.generateCondition(tfA, tfB);
		if (expected == null) {
			assert actual == null : "No commutativity condition expected, but found " + actual.getFormula();
		} else {
			assert actual != null : "Expected commutativity condition " + expected + ", but found none";
			final LBool impl = SmtUtils.checkSatTerm(mScript,
					SmtUtils.and(mScript, axioms, actual.getFormula(), SmtUtils.not(mScript, expected)));
			assert impl == LBool.UNSAT : "Actual condition " + actual.getFormula()
					+ " does not imply expected condition " + expected;
		}
	}

	private void setupSimpleSet() {
		// generic inputs
		x = constructVar("x", "Int");
		y = constructVar("y", "Int");

		// data structure
		a = constructVar("a", "Int");
		b = constructVar("b", "Int");
		sz = constructVar("sz", "Int");

		// generic return values
		r1 = constructVar("r1", "Bool");
		r2 = constructVar("r2", "Bool");
		s1 = constructVar("s1", "Int");
		s2 = constructVar("s2", "Int");

		// UINT values
		axioms = SmtUtils.and(mScript, axioms, nonNegative(x), nonNegative(y));
	}

	private void setupArrayStack() {
		arr = ProgramVarUtils.constructGlobalProgramVarPair("arr",
				mScript.sort("Array", mScript.sort("Int"), mScript.sort("Int")), mMgdScript, null);
		mSymbolTable.add(arr);

		max = constructVar("max", "Int");
		top = constructVar("top", "Int");
		e1 = constructVar("e1", "Int");
		e2 = constructVar("e2", "Int");

		axioms = SmtUtils.and(mScript, axioms, nonNegative(max));
	}

	private IProgramVar constructVar(final String name, final String sort) {
		final IProgramVar variable =
				ProgramVarUtils.constructGlobalProgramVarPair(name, mScript.sort(sort), mMgdScript, null);
		mSymbolTable.add(variable);
		return variable;
	}

	private Term nonNegative(final IProgramVar variable) {
		return SmtUtils.geq(mScript, variable.getTerm(), mScript.numeral("0"));
	}

	public UnmodifiableTransFormula isIn(final IProgramVar arg, final IProgramVar out) {
		final Term term = SmtUtils.or(mScript, SmtUtils.binaryEquality(mScript, arg.getTerm(), a.getTerm()),
				SmtUtils.binaryEquality(mScript, arg.getTerm(), b.getTerm()));
		return TransFormulaBuilder.constructAssignment(Collections.singletonList(out), Collections.singletonList(term),
				mSymbolTable, mMgdScript);
	}

	public UnmodifiableTransFormula getSize(final IProgramVar out) {
		return TransFormulaBuilder.constructAssignment(Collections.singletonList(out),
				Collections.singletonList(sz.getTerm()), mSymbolTable, mMgdScript);
	}

	public UnmodifiableTransFormula clear() {
		return TransFormulaBuilder.constructAssignment(Arrays.asList(a, b, sz),
				Arrays.asList(mScript.numeral("-1"), mScript.numeral("-1"), mScript.numeral("0")), mSymbolTable,
				mMgdScript);
	}

	public UnmodifiableTransFormula add(final IProgramVar arg) {
		final UnmodifiableTransFormula skip = TransFormulaUtils.constructHavoc(Collections.emptySet(), mMgdScript);
		return constructIte(parseWithVariables("(= sz 0)"),
				TransFormulaBuilder.constructAssignment(Arrays.asList(a, sz),
						Arrays.asList(arg.getTerm(), parseWithVariables("(+ sz 1)")), mSymbolTable, mMgdScript),

				constructIte(
						SmtUtils.or(mScript, SmtUtils.binaryEquality(mScript, a.getTerm(), arg.getTerm()),
								SmtUtils.binaryEquality(mScript, b.getTerm(), arg.getTerm())),
						skip,
						constructIte(parseWithVariables("(= a (- 1))"),
								TransFormulaBuilder.constructAssignment(Arrays.asList(a, sz),
										Arrays.asList(arg.getTerm(), parseWithVariables("(+ sz 1)")), mSymbolTable,
										mMgdScript),
								constructIte(parseWithVariables("(= b (- 1))"),
										TransFormulaBuilder.constructAssignment(Arrays.asList(b, sz),
												Arrays.asList(arg.getTerm(), parseWithVariables("(+ sz 1)")),
												mSymbolTable, mMgdScript),
										skip))));
	}

	private UnmodifiableTransFormula pop(final IProgramVar out) {
		return constructIte(parseWithVariables("(= top (- 1))"),
				TransFormulaBuilder.constructAssignment(Arrays.asList(out), Arrays.asList(mScript.numeral("-1")),
						mSymbolTable, mMgdScript),
				TransFormulaBuilder.constructAssignment(Arrays.asList(top, out),
						Arrays.asList(parseWithVariables("(- top 1)"), parseWithVariables("(select arr top)")),
						mSymbolTable, mMgdScript));
	}

	private UnmodifiableTransFormula push(final IProgramVar arg, final IProgramVar out) {
		return constructIte(parseWithVariables("(= top (- max 1))"),
				TransFormulaBuilder.constructAssignment(Arrays.asList(out), Arrays.asList(mScript.term("false")),
						mSymbolTable, mMgdScript),
				TransFormulaBuilder.constructAssignment(Arrays.asList(arr, top, out),
						Arrays.asList(
								SmtUtils.store(mScript, arr.getTerm(), parseWithVariables("(+ top 1)"), arg.getTerm()),
								parseWithVariables("(+ top 1)"), mScript.term("true")),
						mSymbolTable, mMgdScript));
	}

	private UnmodifiableTransFormula isEmpty(final IProgramVar out) {
		return TransFormulaBuilder.constructAssignment(Arrays.asList(out),
				Arrays.asList(SmtUtils.binaryEquality(mScript, top.getTerm(), mScript.numeral("-1"))), mSymbolTable,
				mMgdScript);
	}

	private UnmodifiableTransFormula constructIte(final Term condition, final UnmodifiableTransFormula thenBranch,
			final UnmodifiableTransFormula elseBranch) {
		final UnmodifiableTransFormula takeThen = compose(TransFormulaBuilder.constructTransFormulaFromTerm(condition,
				(Set) mSymbolTable.getGlobals(), mMgdScript), thenBranch);
		final UnmodifiableTransFormula takeElse =
				compose(TransFormulaBuilder.constructTransFormulaFromTerm(SmtUtils.not(mScript, condition),
						(Set) mSymbolTable.getGlobals(), mMgdScript), elseBranch);
		return TransFormulaUtils.parallelComposition(mLogger, mServices, mMgdScript, null, false,
				XnfConversionTechnique.BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION, takeThen, takeElse);
	}

	private UnmodifiableTransFormula compose(final UnmodifiableTransFormula a, final UnmodifiableTransFormula b) {
		return TransFormulaUtils.sequentialComposition(mLogger, mServices, mMgdScript, false, false, false,
				XnfConversionTechnique.BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION, SimplificationTechnique.SIMPLIFY_DDA,
				Arrays.asList(a, b));
	}

	private Term parseWithVariables(final String syntax) {
		final String declarations = mSymbolTable.getGlobals().stream()
				.map(pv -> "(" + pv.getTermVariable().getName() + " " + pv.getSort() + ")")
				.collect(Collectors.joining(" "));
		final String fullSyntax = "(forall (" + declarations + ") " + syntax + ")";
		final QuantifiedFormula quant = (QuantifiedFormula) TermParseUtils.parseTerm(mScript, fullSyntax);
		return new CommuhashNormalForm(mServices, mScript).transform(quant.getSubformula());
	}
}