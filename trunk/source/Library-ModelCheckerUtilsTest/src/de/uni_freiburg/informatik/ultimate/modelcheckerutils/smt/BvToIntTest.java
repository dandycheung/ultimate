package de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt;

import java.io.IOException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger.LogLevel;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.scripttransfer.HistoryRecordingScript;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.ManagedScript;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.QuantifierUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtSortUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.bvinttranslation.TranslationManager;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.quantifier.PartialQuantifierElimination;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.solverbuilder.LoggingScriptForMainTrackBenchmarks;
import de.uni_freiburg.informatik.ultimate.logic.LoggingScript;
import de.uni_freiburg.informatik.ultimate.logic.Logics;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.smtsolver.external.TermParseUtils;
import de.uni_freiburg.informatik.ultimate.test.mocks.UltimateMocks;
import de.uni_freiburg.informatik.ultimate.util.ReflectionUtil;

/**
 *
 * @author Max Barth
 */

public class BvToIntTest {

	private static final boolean WRITE_SMT_SCRIPTS_TO_FILE = false;
	private static final boolean WRITE_MAIN_TRACK_SCRIPT_IF_UNKNOWN_TO_FILE = false;

	private static final String SOLVER_COMMAND_Z3 =
			"z3 SMTLIB2_COMPLIANT=true -t:6000 -memory:2024 -smt2 -in smt.arith.solver=2";
	private static final String SOLVER_COMMAND_CVC4 = "cvc4 --incremental --print-success --lang smt --tlimit-per=6000";
	private static final String SOLVER_COMMAND_MATHSAT = "mathsat";
	/**
	 * If DEFAULT_SOLVER_COMMAND is not null we ignore the solver specified
	 * for each test and use only the solver
	 * specified here. This can be useful to check if there is a suitable
	 * solver for all tests and this can be useful
	 * for generating difficult SMT-LIB benchmarks.
	 */
	private static final String DEFAULT_SOLVER_COMMAND = null;

	private Script mScript;
	private IUltimateServiceProvider mServices;
	private ManagedScript mMgdScript;
	private ILogger mLogger;

	@Before
	public void setUp() throws Exception {
		mServices = UltimateMocks.createUltimateServiceProviderMock();
		mLogger = mServices.getLoggingService().getLogger("lol");
		mScript = UltimateMocks.createZ3Script(LogLevel.INFO);
		mMgdScript = new ManagedScript(mServices, mScript);
		mScript.setLogic(Logics.ALL);

	}

	@After
	public void tearDown() {
		mScript.exit();
	}

	public static Sort getBitvectorSort8(final Script script) {
		return SmtSortUtils.getBitvectorSort(script, 8);
	}

	private Term parse(final String inputSTR) {
		final Term formulaAsTerm = TermParseUtils.parseTerm(mScript, inputSTR);
		return formulaAsTerm;
	}

	private Term translateQelimBacktranslate(final Term input) {
		final TranslationManager translationManager = new TranslationManager(mMgdScript);
		final Term translated = translationManager.translateBvtoInt(input);

		System.out.println("translatedResult: " + translated.toStringDirect());
		final Term qelimResult = PartialQuantifierElimination.eliminate(mServices, mMgdScript, translated,
				SimplificationTechnique.SIMPLIFY_DDA);
		System.out.println("qelimResult: " + qelimResult.toStringDirect());

		final Term backTranslated = translationManager.translateIntBacktoBv(qelimResult);
		System.out.println("backtransresult: " + backTranslated.toStringDirect());
		return backTranslated;
	}

	private void testQelim(final Term t1, final Term t2) {
		final Term qelimResult =
				PartialQuantifierElimination.eliminate(mServices, mMgdScript, t1, SimplificationTechnique.SIMPLIFY_DDA);
		final LBool equi = SmtUtils.checkEquivalence(t1, t2, mScript);
		assert !equi.equals(LBool.UNKNOWN);
		Assert.assertTrue(equi.equals(LBool.UNSAT));
		if (equi.equals(LBool.UNSAT)) {
			Assert.assertTrue(equi.equals(LBool.UNSAT));
		} else if (!QuantifierUtils.isQuantifierFree(qelimResult)) {
			// TODO translate everything
			// TODO not equivalent if we find qfree int formel
			Assert.assertTrue(true);
		} else {
			Assert.assertTrue(false);
		}

	}



	private void setUpScript(final String solverCommand, final VarDecl... varDecls) {
		final Script script = createSolver(solverCommand);
		script.setLogic(Logics.ALL);
		for (final VarDecl varDecl : varDecls) {
			varDecl.declareVars(script);
		}
		mScript = script;
		mMgdScript = new ManagedScript(mServices, script);
	}

	private Script createSolver(final String proviededSolverCommand) {
		String effectiveSolveCommand;
		if (DEFAULT_SOLVER_COMMAND != null) {
			effectiveSolveCommand = DEFAULT_SOLVER_COMMAND;
		} else {
			effectiveSolveCommand = proviededSolverCommand;
		}
		Script result = new HistoryRecordingScript(UltimateMocks.createSolver(effectiveSolveCommand, LogLevel.INFO));
		final String testName = ReflectionUtil.getCallerMethodName(4);
		if (WRITE_SMT_SCRIPTS_TO_FILE) {
			try {
				final String filename = testName + ".smt2";
				result = new LoggingScript(result, filename, true);
			} catch (final IOException e) {
				throw new AssertionError("IOException while constructing LoggingScript");
			}
		}
		if (WRITE_MAIN_TRACK_SCRIPT_IF_UNKNOWN_TO_FILE) {
			final String baseFilename = testName;
			result = new LoggingScriptForMainTrackBenchmarks(result, baseFilename, ".");
		}
		return result;
	}




	// @Test //TODO runtime
	public void dllqueue01() {
		final VarDecl[] funDecls = new VarDecl[] {
				new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "main_~item~0.base", "main_~head~0.offset",
						"main_~item~0.offset", "main_#t~malloc2.offset", "main_#t~malloc2.base", "main_~head~0.base"),
				new VarDecl(QuantifierEliminationTest::getArrayBv32Bv32Sort, "#length"), };
		final String inputSTR =
				"(forall ((|v_#memory_$Pointer$.base_64| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (|v_#memory_$Pointer$.offset_61| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (|#memory_$Pointer$.offset| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (|#memory_$Pointer$.base| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32))))) (or (not (and (= |v_#memory_$Pointer$.base_64| (store |#memory_$Pointer$.base| main_~item~0.base (store (select |#memory_$Pointer$.base| main_~item~0.base) main_~item~0.offset |main_#t~malloc2.base|))) (= |v_#memory_$Pointer$.offset_61| (store |#memory_$Pointer$.offset| main_~item~0.base (store (select |#memory_$Pointer$.offset| main_~item~0.base) main_~item~0.offset |main_#t~malloc2.offset|))))) (and (forall ((v_prxprot_4 (_ BitVec 32)) (v_prxprot_1 (_ BitVec 32)) (v_DerPreprocessor_9 (_ BitVec 32))) (bvsle (bvadd (select (select (store (store |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd v_prxprot_4 (_ bv4 32)) main_~item~0.offset)) (select (select (store |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd v_prxprot_4 (_ bv4 32)) main_~item~0.base)) main_~item~0.base) main_~item~0.offset) (store (store (select (store |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd v_prxprot_4 (_ bv4 32)) main_~item~0.offset)) (select (select (store |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd v_prxprot_4 (_ bv4 32)) main_~item~0.base)) main_~item~0.base) main_~item~0.offset)) v_prxprot_1 (_ bv0 32)) (bvadd v_prxprot_1 (_ bv8 32)) v_DerPreprocessor_9)) main_~head~0.base) main_~head~0.offset) (_ bv8 32)) (bvadd (select (select (store (store |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd v_prxprot_4 (_ bv4 32)) main_~item~0.offset)) (select (select (store |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd v_prxprot_4 (_ bv4 32)) main_~item~0.base)) main_~item~0.base) main_~item~0.offset) (store (store (select (store |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd v_prxprot_4 (_ bv4 32)) main_~item~0.offset)) (select (select (store |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd v_prxprot_4 (_ bv4 32)) main_~item~0.base)) main_~item~0.base) main_~item~0.offset)) v_prxprot_1 (_ bv0 32)) (bvadd v_prxprot_1 (_ bv8 32)) v_DerPreprocessor_9)) main_~head~0.base) main_~head~0.offset) (_ bv12 32)))) (forall ((|main_#t~mem3.offset| (_ BitVec 32)) (v_DerPreprocessor_5 (_ BitVec 32)) (v_DerPreprocessor_10 (_ BitVec 32)) (v_prxprot_2 (_ BitVec 32))) (bvsle (bvadd (select (select (store (store |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd |main_#t~mem3.offset| (_ bv4 32)) main_~item~0.offset)) (select (select (store |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd |main_#t~mem3.offset| (_ bv4 32)) main_~item~0.base)) main_~item~0.base) main_~item~0.offset) (store (store (select (store |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd |main_#t~mem3.offset| (_ bv4 32)) main_~item~0.offset)) (select (select (store |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd |main_#t~mem3.offset| (_ bv4 32)) main_~item~0.base)) main_~item~0.base) main_~item~0.offset)) v_prxprot_2 (_ bv0 32)) (bvadd v_prxprot_2 (_ bv8 32)) v_DerPreprocessor_10)) main_~head~0.base) main_~head~0.offset) (_ bv12 32)) (select |#length| (select (select (store (store |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd |main_#t~mem3.offset| (_ bv4 32)) main_~item~0.base)) (select (select (store |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd |main_#t~mem3.offset| (_ bv4 32)) main_~item~0.base)) main_~item~0.base) main_~item~0.offset) (store (store (select (store |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd |main_#t~mem3.offset| (_ bv4 32)) main_~item~0.base)) (select (select (store |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd |main_#t~mem3.offset| (_ bv4 32)) main_~item~0.base)) main_~item~0.base) main_~item~0.offset)) v_prxprot_2 (_ bv0 32)) (bvadd v_prxprot_2 (_ bv8 32)) v_DerPreprocessor_5)) main_~head~0.base) main_~head~0.offset)))) (forall ((v_subst_2 (_ BitVec 32)) (v_prxprot_3 (_ BitVec 32)) (v_DerPreprocessor_8 (_ BitVec 32))) (bvsle (_ bv0 32) (bvadd (select (select (store (store |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd v_prxprot_3 (_ bv4 32)) main_~item~0.offset)) (select (select (store |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd v_prxprot_3 (_ bv4 32)) main_~item~0.base)) main_~item~0.base) main_~item~0.offset) (store (store (select (store |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.offset_61| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd v_prxprot_3 (_ bv4 32)) main_~item~0.offset)) (select (select (store |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset) (store (select |v_#memory_$Pointer$.base_64| (select (select |v_#memory_$Pointer$.base_64| main_~item~0.base) main_~item~0.offset)) (bvadd v_prxprot_3 (_ bv4 32)) main_~item~0.base)) main_~item~0.base) main_~item~0.offset)) v_subst_2 (_ bv0 32)) (bvadd v_subst_2 (_ bv8 32)) v_DerPreprocessor_8)) main_~head~0.base) main_~head~0.offset) (_ bv8 32)))))))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	// @Test // TODO takes a long time and cannot eliminate all quantifiers
	public void BV_2017_Preiner_scholl_smt08_model_model_6_64() {
		final VarDecl[] funDecls = new VarDecl[] {
				new VarDecl(SmtSortUtils::getBoolSort, "bool.b22", "bool.b7", "bool.b5", "bool.b6", "bool.b23",
						"bool.b12", "bool.b8", "bool.b10", "bool.b14"),
				new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "x3", "x4", "x5"), };
		final String inputSTR =
				"(forall ((?lambda (_ BitVec 32))) (or (not (and (not (and (not (and bool.b6 (not (and (not (and (not (and (not (and (not bool.b12) (not bool.b5) (not bool.b14) (not (bvsle x3 (_ bv723 32))) (bvsle (bvadd x3 (bvmul (_ bv3 32) ?lambda) (bvmul (_ bv3 32) x5)) (_ bv50 32)) (bvsle x3 (_ bv1200 32)) (not bool.b10) (not bool.b8) (bvsle x3 (_ bv40 32)))) (not (bvsle x3 (_ bv0 32))))) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (not (and (not (and (not bool.b5) (not (bvsle (bvadd x4 (bvmul (_ bv60 32) ?lambda)) (_ bv4820 32))))) (bvsle x3 (_ bv0 32)))))) bool.b23)) (not (and (not bool.b23) (not (and (not (and (bvsle x3 (_ bv15 32)) (not (and (bvsle (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)) (_ bv1105 32)) (not (and (not (and (not (and (bvsle x3 (_ bv371 32)) (not (and (not bool.b12) (not bool.b5) (not bool.b14) (not (bvsle x3 (_ bv723 32))) (bvsle x3 (_ bv1200 32)) (not bool.b10) (not bool.b8))))) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (not (and (not (bvsle x3 (_ bv371 32))) (not (and (not bool.b12) (not bool.b5) (not bool.b14) (bvsle x3 (_ bv610 32)) (bvsle x3 (_ bv1200 32)) (not bool.b10) (not bool.b8) (bvsle x3 (_ bv30 32)))))))) (bvsle (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)) (_ bv628 32)))) (not (and (not (bvsle (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)) (_ bv628 32))) (not (and (not bool.b12) (not bool.b5) (not bool.b14) (not bool.b10) (not bool.b8))))))))) (not (and (not (bvsle x3 (_ bv15 32))) (not (and (not (and (bvsle (bvadd x3 (bvmul (_ bv4294967290 32) ?lambda) (bvmul (_ bv4294967290 32) x5)) (_ bv658 32)) (not (and (not bool.b12) (not bool.b5) (not bool.b14) (not (bvsle x3 (_ bv371 32))) (bvsle x3 (_ bv610 32)) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (not bool.b10) (not bool.b8) (bvsle x3 (_ bv30 32)))))) (not (and (not (bvsle (bvadd x3 (bvmul (_ bv4294967290 32) ?lambda) (bvmul (_ bv4294967290 32) x5)) (_ bv658 32))) (not (and (not (and (not (bvsle x3 (_ bv371 32))) (not (and (not bool.b12) (not bool.b5) (not bool.b14) (bvsle x3 (_ bv610 32)) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (not bool.b10) (not bool.b8) (bvsle x3 (_ bv30 32)))))) (not (and (bvsle x3 (_ bv371 32)) (not (and (not bool.b12) (not bool.b5) (not bool.b14) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (not bool.b10) (not bool.b8))))))))))))))))))) bool.b7)) (not bool.b22) (not (and (not (and (not bool.b6) bool.b5)) (not bool.b7))))) (bvslt ?lambda (_ bv0 32)) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (not (and (not (and (not bool.b5) (not (and (not (bvsle (bvmul (_ bv4294967295 32) x3) (_ bv4294967266 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) x4) (bvmul (_ bv4294967236 32) ?lambdaprime)) (_ bv4294962796 32))))) bool.b7 (not bool.b6) (not bool.b22))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (not (and (not bool.b5) (not bool.b6) (not bool.b7) (not (and (not (bvsle (bvmul (_ bv4294967295 32) x3) (_ bv4294967266 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) x4) (bvmul (_ bv4294967236 32) ?lambdaprime)) (_ bv4294963196 32))))) (not bool.b22))) (not (and (not bool.b5) bool.b6 (not (and (not (bvsle (bvmul (_ bv4294967295 32) x3) (_ bv4294967266 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) x4) (bvmul (_ bv4294967236 32) ?lambdaprime)) (_ bv4294962386 32))))) (not bool.b7) (not bool.b22))))) (bvsle ?lambdaprime ?lambda)))))";
		final String expectedResultAsString =
				"(let ((.cse7 (bvsle x3 (_ bv0 32))) (.cse14 (bvsle x3 (_ bv371 32))) (.cse16 (bvsle x3 (_ bv15 32)))) (let ((.cse18 (forall ((?lambda (_ BitVec 32))) (or (bvslt ?lambda (_ bv0 32)) (bvsle (bvadd x4 (bvmul (_ bv60 32) ?lambda)) (_ bv4820 32)) (not (bvsle (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)) (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda)))))) (.cse15 (forall ((?lambda (_ BitVec 32))) (or (bvslt ?lambda (_ bv0 32)) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (not (bvsle (bvadd x3 (bvmul (_ bv3 32) ?lambda) (bvmul (_ bv3 32) x5)) (_ bv50 32)))))) (.cse25 (forall ((?lambda (_ BitVec 32))) (or (bvslt ?lambda (_ bv0 32)) (not (bvsle (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)) (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (not (bvsle (bvadd x3 (bvmul (_ bv3 32) ?lambda) (bvmul (_ bv3 32) x5)) (_ bv50 32)))))) (.cse20 (forall ((?lambda (_ BitVec 32))) (or (bvslt ?lambda (_ bv0 32)) (bvsle (bvadd x4 (bvmul (_ bv60 32) ?lambda)) (_ bv4820 32)) (bvsle (bvadd x3 (bvmul (_ bv4294967290 32) ?lambda) (bvmul (_ bv4294967290 32) x5)) (_ bv658 32)) (not (bvsle (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)) (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (not (bvsle (bvadd x3 (bvmul (_ bv3 32) ?lambda) (bvmul (_ bv3 32) x5)) (_ bv50 32)))))) (.cse28 (forall ((?lambda (_ BitVec 32))) (let ((.cse37 (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)))) (or (bvslt ?lambda (_ bv0 32)) (bvsle (bvadd x4 (bvmul (_ bv60 32) ?lambda)) (_ bv4820 32)) (not (bvsle .cse37 (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (bvsle .cse37 (_ bv628 32)))))) (.cse17 (forall ((?lambda (_ BitVec 32))) (or (bvslt ?lambda (_ bv0 32)) (bvsle (bvadd x4 (bvmul (_ bv60 32) ?lambda)) (_ bv4820 32)) (bvsle (bvadd x3 (bvmul (_ bv4294967290 32) ?lambda) (bvmul (_ bv4294967290 32) x5)) (_ bv658 32)) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (not (bvsle (bvadd x3 (bvmul (_ bv3 32) ?lambda) (bvmul (_ bv3 32) x5)) (_ bv50 32)))))) (.cse3 (not .cse16)) (.cse9 (forall ((?lambda (_ BitVec 32))) (or (bvsle (bvadd x3 (bvmul (_ bv4294967290 32) ?lambda) (bvmul (_ bv4294967290 32) x5)) (_ bv658 32)) (bvslt ?lambda (_ bv0 32)) (not (bvsle (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)) (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (not (bvsle (bvadd x3 (bvmul (_ bv3 32) ?lambda) (bvmul (_ bv3 32) x5)) (_ bv50 32)))))) (.cse19 (forall ((?lambda (_ BitVec 32))) (or (bvsle (bvadd x3 (bvmul (_ bv4294967290 32) ?lambda) (bvmul (_ bv4294967290 32) x5)) (_ bv658 32)) (bvslt ?lambda (_ bv0 32)) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (not (bvsle (bvadd x3 (bvmul (_ bv3 32) ?lambda) (bvmul (_ bv3 32) x5)) (_ bv50 32)))))) (.cse23 (forall ((?lambda (_ BitVec 32))) (or (bvslt ?lambda (_ bv0 32)) (bvsle (bvadd x4 (bvmul (_ bv60 32) ?lambda)) (_ bv4820 32)) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (not (bvsle (bvadd x3 (bvmul (_ bv3 32) ?lambda) (bvmul (_ bv3 32) x5)) (_ bv50 32)))))) (.cse8 (not (bvsle x3 (_ bv40 32)))) (.cse24 (forall ((?lambda (_ BitVec 32))) (or (bvslt ?lambda (_ bv0 32)) (bvsle (bvadd x4 (bvmul (_ bv60 32) ?lambda)) (_ bv4820 32)) (not (bvsle (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)) (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (not (bvsle (bvadd x3 (bvmul (_ bv3 32) ?lambda) (bvmul (_ bv3 32) x5)) (_ bv50 32)))))) (.cse1 (forall ((?lambda (_ BitVec 32))) (or (bvslt ?lambda (_ bv0 32)) (not (bvsle (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)) (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda)))))) (.cse34 (forall ((?lambda (_ BitVec 32))) (or (bvslt ?lambda (_ bv0 32)) (bvsle (bvadd x4 (bvmul (_ bv60 32) ?lambda)) (_ bv4820 32)) (bvsle (bvadd x3 (bvmul (_ bv4294967290 32) ?lambda) (bvmul (_ bv4294967290 32) x5)) (_ bv658 32)) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda)))))) (.cse21 (forall ((?lambda (_ BitVec 32))) (or (bvsle (bvadd x3 (bvmul (_ bv4294967290 32) ?lambda) (bvmul (_ bv4294967290 32) x5)) (_ bv658 32)) (bvslt ?lambda (_ bv0 32)) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda)))))) (.cse26 (forall ((?lambda (_ BitVec 32))) (or (bvslt ?lambda (_ bv0 32)) (bvsle (bvadd x4 (bvmul (_ bv60 32) ?lambda)) (_ bv4820 32)) (bvsle (bvadd x3 (bvmul (_ bv4294967290 32) ?lambda) (bvmul (_ bv4294967290 32) x5)) (_ bv658 32)) (not (bvsle (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)) (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda)))))) (.cse0 (not .cse14)) (.cse11 (forall ((?lambda (_ BitVec 32))) (let ((.cse36 (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)))) (or (bvslt ?lambda (_ bv0 32)) (bvsle (bvadd x4 (bvmul (_ bv60 32) ?lambda)) (_ bv4820 32)) (bvsle (bvadd x3 (bvmul (_ bv4294967290 32) ?lambda) (bvmul (_ bv4294967290 32) x5)) (_ bv658 32)) (not (bvsle .cse36 (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (bvsle .cse36 (_ bv628 32)))))) (.cse4 (bvsle x3 (_ bv723 32))) (.cse33 (forall ((?lambda (_ BitVec 32))) (or (bvsle (bvadd x3 (bvmul (_ bv4294967290 32) ?lambda) (bvmul (_ bv4294967290 32) x5)) (_ bv658 32)) (bvslt ?lambda (_ bv0 32)) (not (bvsle (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)) (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda)))))) (.cse6 (not (bvsle x3 (_ bv1200 32)))) (.cse22 (not bool.b23)) (.cse13 (not .cse7)) (.cse29 (forall ((?lambda (_ BitVec 32))) (let ((.cse35 (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)))) (or (bvsle (bvadd x3 (bvmul (_ bv4294967290 32) ?lambda) (bvmul (_ bv4294967290 32) x5)) (_ bv658 32)) (bvslt ?lambda (_ bv0 32)) (not (bvsle .cse35 (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (bvsle .cse35 (_ bv628 32)))))) (.cse2 (not bool.b6)) (.cse10 (not (bvsle x3 (_ bv30 32)))) (.cse5 (not bool.b7)) (.cse12 (not (bvsle x3 (_ bv610 32))))) (and (or bool.b8 .cse0 .cse1 .cse2 bool.b10 .cse3 bool.b12 bool.b23 bool.b22 bool.b5 .cse4 .cse5 bool.b14 .cse6) (or bool.b8 .cse0 .cse2 .cse7 .cse8 bool.b10 bool.b12 bool.b22 bool.b5 .cse4 .cse9 .cse5 bool.b14 .cse6) (or bool.b8 .cse2 .cse10 bool.b10 .cse11 bool.b12 bool.b22 .cse5 bool.b14 .cse12 bool.b5 .cse13) (or bool.b8 .cse2 .cse7 .cse8 bool.b10 bool.b12 bool.b22 .cse12 bool.b5 .cse14 .cse4 .cse15 .cse10 .cse16 .cse5 bool.b14 .cse6) (or bool.b8 .cse2 .cse8 bool.b10 bool.b12 .cse17 bool.b22 .cse12 bool.b5 .cse4 .cse10 .cse16 .cse5 bool.b14 .cse6) (or bool.b8 .cse2 bool.b10 bool.b12 bool.b22 .cse12 bool.b5 .cse14 .cse10 .cse5 bool.b14 .cse6 .cse18 .cse13) (or bool.b8 .cse2 .cse7 .cse8 bool.b10 bool.b12 bool.b22 .cse12 bool.b5 .cse4 .cse10 .cse16 .cse19 .cse5 bool.b14 .cse6) (or bool.b8 .cse0 .cse2 bool.b10 .cse3 bool.b12 bool.b22 bool.b5 .cse4 .cse5 bool.b14 .cse6 .cse18 .cse13) (or bool.b8 .cse0 .cse2 .cse8 bool.b10 bool.b12 bool.b22 bool.b5 .cse20 .cse4 .cse5 bool.b14 .cse6) (or bool.b8 .cse2 bool.b10 .cse3 bool.b12 bool.b22 .cse12 bool.b5 .cse4 .cse10 .cse5 bool.b14 .cse6 .cse18 .cse13) (or bool.b8 .cse0 .cse2 bool.b10 .cse16 bool.b12 bool.b23 .cse21 bool.b22 .cse5 bool.b14 bool.b5) (or bool.b8 .cse1 .cse2 bool.b10 .cse3 bool.b12 bool.b23 bool.b22 .cse12 bool.b5 .cse4 .cse10 .cse5 bool.b14 .cse6) (or bool.b8 .cse2 .cse7 .cse8 bool.b10 .cse22 bool.b12 bool.b22 bool.b5 .cse4 .cse15 .cse5 bool.b14 .cse6) (or bool.b8 .cse2 bool.b10 bool.b12 bool.b22 .cse12 bool.b5 .cse14 .cse10 .cse16 .cse5 bool.b14 (forall ((?lambda (_ BitVec 32))) (or (bvslt ?lambda (_ bv0 32)) (bvsle (bvadd x4 (bvmul (_ bv60 32) ?lambda)) (_ bv4820 32)) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))))) .cse13) (or bool.b8 .cse2 .cse8 bool.b10 bool.b12 bool.b22 .cse12 bool.b5 .cse14 .cse4 .cse23 .cse10 .cse16 .cse5 bool.b14 .cse6) (or bool.b8 .cse0 .cse2 .cse8 bool.b10 .cse3 bool.b12 bool.b22 bool.b5 .cse4 .cse24 .cse5 bool.b14 .cse6) (or bool.b8 .cse2 .cse7 .cse8 .cse25 bool.b10 .cse3 bool.b12 bool.b22 .cse12 bool.b5 .cse4 .cse10 .cse5 bool.b14 .cse6) (or bool.b8 .cse0 .cse2 .cse7 .cse8 .cse25 bool.b10 .cse3 bool.b12 bool.b22 bool.b5 .cse4 .cse5 bool.b14 .cse6) (or bool.b8 .cse2 .cse8 bool.b10 .cse3 bool.b12 bool.b22 .cse12 bool.b5 .cse4 .cse10 .cse24 .cse5 bool.b14 .cse6) (or bool.b8 .cse0 .cse26 .cse2 bool.b10 bool.b12 bool.b22 bool.b5 .cse4 .cse5 bool.b14 .cse6 .cse13) (or bool.b8 .cse2 bool.b10 .cse3 bool.b23 bool.b12 bool.b22 .cse5 bool.b14 (forall ((?lambda (_ BitVec 32))) (let ((.cse27 (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)))) (or (bvslt ?lambda (_ bv0 32)) (not (bvsle .cse27 (_ bv1105 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (bvsle .cse27 (_ bv628 32))))) bool.b5) (or bool.b8 .cse2 .cse7 .cse8 .cse25 bool.b10 bool.b12 bool.b22 .cse12 bool.b5 .cse14 .cse4 .cse10 .cse5 bool.b14 .cse6) (or bool.b8 .cse2 bool.b10 .cse3 bool.b12 bool.b22 .cse5 bool.b14 .cse28 bool.b5 .cse13) (or bool.b8 .cse2 bool.b10 bool.b12 bool.b23 bool.b22 .cse12 bool.b5 .cse14 .cse10 .cse16 .cse5 bool.b14 (forall ((?lambda (_ BitVec 32))) (or (bvslt ?lambda (_ bv0 32)) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda)))))) (or bool.b8 .cse29 .cse0 .cse2 bool.b10 bool.b12 bool.b23 bool.b22 .cse5 bool.b14 bool.b5) (or bool.b8 .cse2 .cse8 bool.b10 bool.b12 bool.b22 .cse12 bool.b5 .cse20 .cse4 .cse10 .cse5 bool.b14 .cse6) (or bool.b8 .cse2 bool.b10 bool.b12 bool.b22 .cse12 bool.b5 .cse14 .cse10 .cse5 bool.b14 .cse28 .cse13) (or bool.b8 .cse2 .cse7 .cse8 bool.b10 .cse3 bool.b12 bool.b22 bool.b5 .cse4 (forall ((?lambda (_ BitVec 32))) (let ((.cse30 (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)))) (or (bvslt ?lambda (_ bv0 32)) (not (bvsle .cse30 (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (not (bvsle (bvadd x3 (bvmul (_ bv3 32) ?lambda) (bvmul (_ bv3 32) x5)) (_ bv50 32))) (bvsle .cse30 (_ bv628 32))))) .cse5 bool.b14 .cse6) (or bool.b8 .cse2 bool.b10 (forall ((?lambda (_ BitVec 32))) (let ((.cse31 (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)))) (or (bvslt ?lambda (_ bv0 32)) (not (bvsle .cse31 (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (bvsle .cse31 (_ bv628 32))))) bool.b12 bool.b23 bool.b22 .cse12 bool.b5 .cse14 .cse10 .cse5 bool.b14) (or bool.b8 .cse0 .cse2 .cse8 bool.b10 bool.b12 .cse17 bool.b22 bool.b5 .cse4 .cse16 .cse5 bool.b14 .cse6) (or bool.b8 .cse2 .cse8 bool.b10 .cse3 bool.b12 bool.b22 bool.b5 .cse4 (forall ((?lambda (_ BitVec 32))) (let ((.cse32 (bvadd x3 (bvmul (_ bv4294967287 32) ?lambda) (bvmul (_ bv4294967287 32) x5)))) (or (bvslt ?lambda (_ bv0 32)) (bvsle (bvadd x4 (bvmul (_ bv60 32) ?lambda)) (_ bv4820 32)) (not (bvsle .cse32 (_ bv1105 32))) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))) (not (bvsle (bvadd x3 (bvmul (_ bv3 32) ?lambda) (bvmul (_ bv3 32) x5)) (_ bv50 32))) (bvsle .cse32 (_ bv628 32))))) .cse5 bool.b14 .cse6) (or bool.b8 .cse2 .cse7 .cse8 bool.b10 bool.b12 bool.b22 .cse12 bool.b5 .cse4 .cse9 .cse10 .cse5 bool.b14 .cse6) (or bool.b8 .cse0 .cse2 bool.b10 bool.b12 bool.b23 bool.b22 bool.b5 .cse4 .cse33 .cse5 bool.b14 .cse6) (or bool.b8 .cse0 .cse2 .cse7 .cse8 bool.b10 bool.b12 bool.b22 bool.b5 .cse4 .cse16 .cse19 .cse5 bool.b14 .cse6) (or bool.b8 .cse2 .cse8 bool.b10 .cse22 bool.b12 bool.b22 bool.b5 .cse4 .cse23 .cse5 bool.b14 .cse6) (or bool.b8 .cse2 bool.b10 bool.b12 bool.b22 .cse12 bool.b5 .cse10 .cse16 .cse34 .cse5 bool.b14 .cse13) (or bool.b8 .cse2 .cse8 bool.b10 bool.b12 bool.b22 .cse12 bool.b5 .cse14 .cse4 .cse10 .cse24 .cse5 bool.b14 .cse6) (or bool.b8 .cse1 .cse2 bool.b10 bool.b12 bool.b23 bool.b22 .cse12 bool.b5 .cse14 .cse10 .cse5 bool.b14 .cse6) (or bool.b8 .cse0 .cse2 bool.b10 .cse16 bool.b12 bool.b22 .cse34 .cse5 bool.b14 bool.b5 .cse13) (or bool.b8 .cse2 bool.b10 bool.b12 bool.b23 bool.b22 .cse12 bool.b5 .cse10 .cse16 .cse21 .cse5 bool.b14) (or bool.b8 .cse26 .cse2 bool.b10 bool.b12 bool.b22 .cse12 bool.b5 .cse4 .cse10 .cse5 bool.b14 .cse6 .cse13) (or bool.b8 .cse0 .cse2 bool.b10 .cse11 bool.b12 bool.b22 .cse5 bool.b14 bool.b5 .cse13) (or (not bool.b5) bool.b6 bool.b7 (forall ((?lambda (_ BitVec 32))) (or (bvslt ?lambda (_ bv0 32)) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))))) bool.b22) (or bool.b8 .cse2 bool.b10 bool.b12 bool.b23 bool.b22 .cse12 bool.b5 .cse4 .cse33 .cse10 .cse5 bool.b14 .cse6) (or .cse2 .cse22 bool.b22 .cse5 bool.b5 (forall ((?lambda (_ BitVec 32))) (or (bvsle (bvadd x4 (bvmul (_ bv60 32) ?lambda)) (_ bv4820 32)) (bvslt ?lambda (_ bv0 32)) (not (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambda) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32))) (exists ((?lambdaprime (_ BitVec 32))) (and (bvsle (_ bv0 32) ?lambdaprime) (bvsle (bvadd (bvmul (_ bv4294967295 32) ?lambdaprime) (bvmul (_ bv4294967295 32) x5)) (_ bv4294967286 32)) (bvsle ?lambdaprime ?lambda))))) .cse13) (or bool.b8 .cse29 .cse2 .cse10 bool.b10 bool.b12 bool.b23 bool.b22 .cse5 bool.b14 .cse12 bool.b5))))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void arrayEliminationRushingMountaineer02() {
		final VarDecl[] funDecls =
				new VarDecl[] { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "~#a~0.base"),
						new VarDecl(QuantifierEliminationTest::getArrayBv32Bv1Sort, "#valid"), };
		final String inputSTR =
				"(exists ((|v_#valid_34| (Array (_ BitVec 32) (_ BitVec 1))) (|#t~string0.base| (_ BitVec 32)) (|#t~string3.base| (_ BitVec 32)) (|#t~string6.base| (_ BitVec 32)) (|#t~string9.base| (_ BitVec 32)) (|#t~string12.base| (_ BitVec 32)) (|#t~string15.base| (_ BitVec 32))) (= (store (store (store (store (store (store (store |v_#valid_34| (_ bv0 32) (_ bv0 1)) |#t~string0.base| (_ bv1 1)) |#t~string3.base| (_ bv1 1)) |#t~string6.base| (_ bv1 1)) |#t~string9.base| (_ bv1 1)) |#t~string12.base| (_ bv1 1)) |#t~string15.base| (_ bv1 1)) |#valid|))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void heap_data_cart2() {
		final VarDecl[] funDecls =
				new VarDecl[] { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "idxDim1", "idxDim2"),
						new VarDecl(QuantifierEliminationTest::getArrayBv32Bv32Bv32Sort, "arr"), };
		final String inputSTR =
				"(and (= idxDim2 (_ bv0 32)) (exists ((x (_ BitVec 32))) (and (exists ((|â| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (y (_ BitVec 32)) (z Bool)) (and (or (and (not (bvslt (select (select |â| y) (_ bv4 32)) x)) (not z)) (and (bvslt (select (select |â| y) (_ bv4 32)) x) z)) (= (store |â| y (store (store (select |â| y) (_ bv8 32) x) (_ bv4 32) (select (store (select |â| y) (_ bv8 32) x) (_ bv4 32)))) arr) (not (bvslt (select (select |â| idxDim1) (bvadd idxDim2 (_ bv4 32))) (select (select |â| idxDim1) (bvadd idxDim2 (_ bv8 32))))) (not (bvslt (select (select |â| idxDim1) (bvadd idxDim2 (_ bv8 32))) (_ bv0 32))) (not z))) (not (bvslt x (_ bv0 32))))))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void memleaks_test1_3() {
		final VarDecl[] funDecls =
				new VarDecl[] { new VarDecl(QuantifierEliminationTest::getArrayBv32Bv1Sort, "#valid", "old(#valid)"), };
		final String inputSTR =
				"(forall ((|v_old(#valid)_BEFORE_CALL_3| (Array (_ BitVec 32) (_ BitVec 1)))) (or (= |#valid| |v_old(#valid)_BEFORE_CALL_3|) (not (forall ((v_entry_point_~p~0.base_12 (_ BitVec 32))) (or (not (= (select |old(#valid)| v_entry_point_~p~0.base_12) (_ bv0 1))) (= |v_old(#valid)_BEFORE_CALL_3| (store |old(#valid)| v_entry_point_~p~0.base_12 (_ bv0 1))))))))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void memleaks_test4_2() {
		final VarDecl[] funDecls =
				new VarDecl[] { new VarDecl(QuantifierEliminationTest::getArrayBv32Bv1Sort, "#valid", "old(#valid)"), };
		final String inputSTR =
				"(forall ((|v_old(#valid)_BEFORE_CALL_8| (Array (_ BitVec 32) (_ BitVec 1)))) (or (not (forall ((v_entry_point_~p~0.base_23 (_ BitVec 32)) (v_entry_point_~q~0.base_17 (_ BitVec 32))) (or (= (store (store (store |old(#valid)| v_entry_point_~p~0.base_23 (_ bv1 1)) v_entry_point_~q~0.base_17 (_ bv0 1)) v_entry_point_~p~0.base_23 (_ bv0 1)) |v_old(#valid)_BEFORE_CALL_8|) (not (= (_ bv0 1) (select |old(#valid)| v_entry_point_~p~0.base_23))) (not (= (select (store |old(#valid)| v_entry_point_~p~0.base_23 (_ bv1 1)) v_entry_point_~q~0.base_17) (_ bv0 1)))))) (= |#valid| |v_old(#valid)_BEFORE_CALL_8|)))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void FakeFloatingMountaineer01() {
		final VarDecl[] funDecls = new VarDecl[] {
				new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "~a~0", "~#f~0.offset", "~g~0"),
				new VarDecl(SmtSortUtils::getRoundingmodeSort, "currentRoundingMode"), };
		final String inputSTR =
				"(forall ((|~#f~0.base| (_ BitVec 32)) (|v_#memory_int_38| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (|#memory_int| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (|v_skolemized_q#valueAsBitvector_3| (_ BitVec 32))) (or (not (= |v_#memory_int_38| (store |#memory_int| |~#f~0.base| (store (select |#memory_int| |~#f~0.base|) (bvadd (bvmul ~a~0 (_ bv4 32)) |~#f~0.offset|) |v_skolemized_q#valueAsBitvector_3|)))) (not (= ((_ to_fp 8 24) currentRoundingMode ~g~0) (fp ((_ extract 31 31) |v_skolemized_q#valueAsBitvector_3|) ((_ extract 30 23) |v_skolemized_q#valueAsBitvector_3|) ((_ extract 22 0) |v_skolemized_q#valueAsBitvector_3|)))) (forall ((|v_skolemized_q#valueAsBitvector_9| (_ BitVec 32)) (|v_skolemized_q#valueAsBitvector_7| (_ BitVec 32)) (|v_skolemized_q#valueAsBitvector_8| (_ BitVec 32)) (|v_skolemized_q#valueAsBitvector_10| (_ BitVec 32)) (|v_skolemized_q#valueAsBitvector_11| (_ BitVec 32)) (|v_skolemized_q#valueAsBitvector_12| (_ BitVec 32)) (|v_skolemized_q#valueAsBitvector_5| (_ BitVec 32)) (|v_skolemized_q#valueAsBitvector_6| (_ BitVec 32)) (|v_skolemized_q#valueAsBitvector_4| (_ BitVec 32))) (= (_ bv0 8) ((_ extract 7 0) (select (let ((.cse0 (bvmul ~a~0 (_ bv4 32)))) (store (store (store (store (store (store (store (store (store (select |v_#memory_int_38| |~#f~0.base|) (bvadd .cse0 |~#f~0.offset| (_ bv4 32)) |v_skolemized_q#valueAsBitvector_4|) (bvadd .cse0 |~#f~0.offset| (_ bv8 32)) |v_skolemized_q#valueAsBitvector_5|) (bvadd .cse0 |~#f~0.offset| (_ bv12 32)) |v_skolemized_q#valueAsBitvector_6|) (bvadd .cse0 |~#f~0.offset| (_ bv16 32)) |v_skolemized_q#valueAsBitvector_7|) (bvadd .cse0 |~#f~0.offset| (_ bv20 32)) |v_skolemized_q#valueAsBitvector_8|) (bvadd .cse0 |~#f~0.offset| (_ bv24 32)) |v_skolemized_q#valueAsBitvector_9|) (bvadd .cse0 |~#f~0.offset| (_ bv28 32)) |v_skolemized_q#valueAsBitvector_10|) (bvadd .cse0 |~#f~0.offset| (_ bv32 32)) |v_skolemized_q#valueAsBitvector_11|) (bvadd .cse0 |~#f~0.offset| (_ bv36 32)) |v_skolemized_q#valueAsBitvector_12|)) |~#f~0.offset|))))))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	// @Test //TODO runtime
	public void forester_heap_dll_01() {
		final VarDecl[] funDecls = new VarDecl[] {
				new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "main_#t~malloc9.base", "main_~end~0.offset",
						"main_~list~0.base", "main_~end~0.base", "main_~list~0.offset"),
				new VarDecl(QuantifierEliminationTest::getArrayBv32Bv1Sort, "#valid"), };
		final String inputSTR =
				"(forall ((|#memory_$Pointer$.base| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (|main_#t~mem10.offset| (_ BitVec 32)) (v_subst_7 (_ BitVec 32))) (= (_ bv0 1) (bvadd (select |#valid| (select (select (store (store (store |#memory_$Pointer$.base| main_~end~0.base (store (select |#memory_$Pointer$.base| main_~end~0.base) main_~end~0.offset |main_#t~malloc9.base|)) |main_#t~malloc9.base| (store (select (store |#memory_$Pointer$.base| main_~end~0.base (store (select |#memory_$Pointer$.base| main_~end~0.base) main_~end~0.offset |main_#t~malloc9.base|)) |main_#t~malloc9.base|) (bvadd |main_#t~mem10.offset| (_ bv4 32)) main_~end~0.base)) (select (select (store (store |#memory_$Pointer$.base| main_~end~0.base (store (select |#memory_$Pointer$.base| main_~end~0.base) main_~end~0.offset |main_#t~malloc9.base|)) |main_#t~malloc9.base| (store (select (store |#memory_$Pointer$.base| main_~end~0.base (store (select |#memory_$Pointer$.base| main_~end~0.base) main_~end~0.offset |main_#t~malloc9.base|)) |main_#t~malloc9.base|) (bvadd |main_#t~mem10.offset| (_ bv4 32)) main_~end~0.base)) main_~end~0.base) main_~end~0.offset) (store (store (select (store (store |#memory_$Pointer$.base| main_~end~0.base (store (select |#memory_$Pointer$.base| main_~end~0.base) main_~end~0.offset |main_#t~malloc9.base|)) |main_#t~malloc9.base| (store (select (store |#memory_$Pointer$.base| main_~end~0.base (store (select |#memory_$Pointer$.base| main_~end~0.base) main_~end~0.offset |main_#t~malloc9.base|)) |main_#t~malloc9.base|) (bvadd |main_#t~mem10.offset| (_ bv4 32)) main_~end~0.base)) (select (select (store (store |#memory_$Pointer$.base| main_~end~0.base (store (select |#memory_$Pointer$.base| main_~end~0.base) main_~end~0.offset |main_#t~malloc9.base|)) |main_#t~malloc9.base| (store (select (store |#memory_$Pointer$.base| main_~end~0.base (store (select |#memory_$Pointer$.base| main_~end~0.base) main_~end~0.offset |main_#t~malloc9.base|)) |main_#t~malloc9.base|) (bvadd |main_#t~mem10.offset| (_ bv4 32)) main_~end~0.base)) main_~end~0.base) main_~end~0.offset)) v_subst_7 (_ bv0 32)) (bvadd v_subst_7 (_ bv8 32)) (_ bv0 32))) main_~list~0.base) main_~list~0.offset)) (_ bv1 1))))";
		final String expectedResultAsString =
				"(forall ((|main_#t~mem10.offset| (_ BitVec 32)) (v_arrayElimCell_156 (_ BitVec 32)) (v_subst_7 (_ BitVec 32))) (or (and (= main_~list~0.offset v_subst_7) (= (_ bv0 1) (bvadd (select |#valid| (_ bv0 32)) (_ bv1 1))) (= |main_#t~malloc9.base| main_~list~0.base)) (and (or (not (= |main_#t~malloc9.base| main_~list~0.base)) (not (= main_~list~0.offset v_subst_7))) (or (and (or (not (= |main_#t~malloc9.base| main_~list~0.base)) (not (= main_~list~0.offset (bvadd v_subst_7 (_ bv8 32))))) (or (and (= main_~list~0.base main_~end~0.base) (= (_ bv0 1) (bvadd (select |#valid| |main_#t~malloc9.base|) (_ bv1 1))) (= main_~list~0.offset main_~end~0.offset)) (and (or (and (= main_~list~0.offset (bvadd |main_#t~mem10.offset| (_ bv4 32))) (= (_ bv0 1) (bvadd (select |#valid| main_~end~0.base) (_ bv1 1))) (= |main_#t~malloc9.base| main_~list~0.base)) (and (= (bvadd (select |#valid| v_arrayElimCell_156) (_ bv1 1)) (_ bv0 1)) (or (not (= |main_#t~malloc9.base| main_~list~0.base)) (not (= main_~list~0.offset (bvadd |main_#t~mem10.offset| (_ bv4 32))))))) (or (not (= main_~list~0.base main_~end~0.base)) (not (= main_~list~0.offset main_~end~0.offset)))))) (and (= main_~list~0.offset (bvadd v_subst_7 (_ bv8 32))) (= (_ bv0 1) (bvadd (select |#valid| (_ bv0 32)) (_ bv1 1))) (= |main_#t~malloc9.base| main_~list~0.base))))))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}







	@Test
	public void arrayEliminationRushingMountaineer01() {
		final VarDecl[] funDecls =
				new VarDecl[] { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "~#a~0.base"),
						new VarDecl(QuantifierEliminationTest::getArrayBv32Bv1Sort, "#valid"), };
		final String inputSTR =
				"(exists ((|v_#valid_16| (Array (_ BitVec 32) (_ BitVec 1))) (|#t~string2.base| (_ BitVec 32))) (= (store (store (store |v_#valid_16| (_ bv0 32) (_ bv0 1)) |#t~string2.base| (_ bv1 1)) |~#a~0.base| (_ bv1 1)) |#valid|))";
		final String expectedResult =
				"(and (exists ((|#t~string2.base| (_ BitVec 32))) (and (= (_ bv1 1) (select |#valid| |#t~string2.base|)) (or (= (_ bv0 32) |#t~string2.base|) (= (_ bv0 1) (select |#valid| (_ bv0 32))) (= (_ bv0 32) |~#a~0.base|)))) (= (_ bv1 1) (select |#valid| |~#a~0.base|)))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void arrayEliminationRushingMountaineer01Reduced() {
		final VarDecl[] funDecls = new VarDecl[] { new VarDecl(QuantifierEliminationTest::getArrayBv32Bv1Sort, "a"), };
		final String inputSTR =
				"(exists ((ax (Array (_ BitVec 32) (_ BitVec 1))) (kx (_ BitVec 32))) (= (store (store ax (_ bv0 32) (_ bv0 1)) kx (_ bv1 1)) a))";
		final String expectedResult =
				"(exists ((kx (_ BitVec 32))) (and (= (select a kx) (_ bv1 1)) (or (= kx (_ bv0 32)) (= (select a (_ bv0 32)) (_ bv0 1)))))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void arrayEliminationRushingMountaineer03() {
		final VarDecl[] funDecls =
				new VarDecl[] { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "main_~x0~0.base"),
						new VarDecl(QuantifierEliminationTest::getArrayBv32Bv1Sort, "#valid"), };
		final String inputSTR =
				"(exists ((|v_#valid_64| (Array (_ BitVec 32) (_ BitVec 1))) (|main_#t~malloc1.base| (_ BitVec 32)) (|main_#t~malloc2.base| (_ BitVec 32)) (|main_#t~malloc3.base| (_ BitVec 32))) (and (= (store (store (store (store |v_#valid_64| main_~x0~0.base (_ bv1 1)) |main_#t~malloc1.base| (_ bv1 1)) |main_#t~malloc2.base| (_ bv1 1)) |main_#t~malloc3.base| (_ bv1 1)) |#valid|) (= (select (store (store (store |v_#valid_64| main_~x0~0.base (_ bv1 1)) |main_#t~malloc1.base| (_ bv1 1)) |main_#t~malloc2.base| (_ bv1 1)) |main_#t~malloc3.base|) (_ bv0 1)) (= (_ bv0 1) (select (store |v_#valid_64| main_~x0~0.base (_ bv1 1)) |main_#t~malloc1.base|))))";
		final String expectedResult =
				"(and (exists ((|main_#t~malloc1.base| (_ BitVec 32)) (|main_#t~malloc3.base| (_ BitVec 32)) (|main_#t~malloc2.base| (_ BitVec 32))) (and (= (select |#valid| |main_#t~malloc1.base|) (_ bv1 1)) (not (= main_~x0~0.base |main_#t~malloc3.base|)) (= (_ bv1 1) (select |#valid| |main_#t~malloc2.base|)) (not (= |main_#t~malloc3.base| |main_#t~malloc1.base|)) (not (= |main_#t~malloc2.base| |main_#t~malloc3.base|)) (not (= main_~x0~0.base |main_#t~malloc1.base|)) (= (select |#valid| |main_#t~malloc3.base|) (_ bv1 1)))) (= (select |#valid| main_~x0~0.base) (_ bv1 1)))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void arrayEliminationFourSeasonsTotalLandscaping() {
		final VarDecl[] funDecls =
				new VarDecl[] { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "nonMain_~src~0.offset"),
						new VarDecl(QuantifierEliminationTest::getArrayBv32Bv1Sort, "#valid"), };
		final String inputSTR =
				"(forall ((|#length| (Array (_ BitVec 32) (_ BitVec 32))) (|nonMain_~src~0.base| (_ BitVec 32))) (or (not (bvule (bvadd nonMain_~src~0.offset (_ bv2 32)) (select |#length| nonMain_~src~0.base))) (bvule nonMain_~src~0.offset (bvadd nonMain_~src~0.offset (_ bv2 32)))))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}



	@Test
	public void derBitvectorFail01() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "~g~0", "main_~a~0") };
		final String inputSTR =
				"(forall ((v_~g~0_24 (_ BitVec 32))) (or (not (= ~g~0 (bvadd v_~g~0_24 (_ bv4294967295 32)))) (= (bvadd main_~a~0 (_ bv1 32)) v_~g~0_24)))";
		final String expectedResult = "(= (bvadd main_~a~0 (_ bv1 32)) (bvadd ~g~0 (_ bv1 32)))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}





	@Test
	public void bvuleTIR() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "lo", "hi") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvule x hi ) (bvule lo x)))";
		final String expectedResult = "(bvule lo hi)";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}
	@Test
	public void bvugeTIR() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "lo", "hi") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvuge hi x  ) (bvuge  x lo)))";
		final String expectedResult = "(bvuge hi lo)";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvsgeTIR() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "lo", "hi") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvsge hi x  ) (bvsge x lo)))";
		final String expectedResult = "(bvsge hi lo)";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvsleTIR() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "lo", "hi") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvsle x hi ) (bvsle lo x)))";
		final String expectedResult = "(bvsle lo hi)";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTIRSignedUnsignedMix() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "lo", "hi") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvsle x hi ) (bvule lo x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTir01() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "lo", "hi") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvsgt hi x) (bvsle lo x)))";
		final String expectedResult = "(bvslt lo hi)";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTir02() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "lo", "hi", "mi") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvsgt hi mi) (bvsge mi x) (bvsle lo x)))";
		final String expectedResult = "(and (bvsle lo mi) (bvsgt hi mi))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSimplify() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "lo", "hi", "mi") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvsgt hi mi) (bvsge mi x) (bvsle hi x)))";
		final String expectedResult = "false";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTir03Strict() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "lo", "hi") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvugt hi x) (bvult lo x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirBug01() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "lo", "hi") };
		final String inputSTR =
				"(exists ((main_~x~0 (_ BitVec 32))) (and (bvsgt main_~x~0 (_ bv100 32)) (not (= (bvadd main_~x~0 (_ bv4294967286 32)) (_ bv91 32))) (not (bvsgt main_~x~0 (_ bv101 32)))))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirBug02() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "lo", "hi") };
		final String inputSTR =
				"(exists ((main_~x~0 (_ BitVec 32))) (and (not (= (bvadd main_~x~0 (_ bv4294967286 32)) (_ bv91 32)))  (bvsge main_~x~0 (_ bv101 32))))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirBug03strict() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "lo", "hi", "y") };
		final String inputSTR =
				"(exists ((x (_ BitVec 8))) (and (not (=  y x)) (bvule lo x) (bvult x hi) (bvule lo y) (bvult y hi)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirBug04nonstrict() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "lo", "hi", "y") };
		final String inputSTR =
				"(exists ((x (_ BitVec 8))) (and (not (=  y x)) (bvule lo x) (bvule x hi) (bvule lo y) (bvult y hi)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionExistsLowerUnsigned() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "a", "b") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvult x a) (bvugt b x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionExistsLowerSigned() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "a", "b") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvslt x a) (bvsgt b x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionExistsUpperUnsigned() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "a", "b") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvugt x a) (bvult b x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionExistsUpperSigned() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "a", "b") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvsgt x a) (bvslt b x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionForallLowerUnsigned() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "a", "b") };
		final String inputSTR = "(forall ((x (_ BitVec 8))) (or (bvule x a) (bvuge b x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionForallLowerSigned() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "a", "b") };
		final String inputSTR = "(forall ((x (_ BitVec 8))) (or (bvsle x a) (bvsge b x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionForallUpperUnsigned() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "a", "b") };
		final String inputSTR = "(forall ((x (_ BitVec 8))) (or (bvuge x a) (bvule b x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionForallUpperSigned() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "a", "b") };
		final String inputSTR = "(forall ((x (_ BitVec 8))) (or (bvsge x a) (bvsle b x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionAndAntiDerUltExists() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "c", "a", "b") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvult x c) (distinct x b)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionAndAntiDerUleExists() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "a", "b") };
		final String inputSTR = "(exists ((x (_ BitVec 32))) (and (bvule a x) (distinct b x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionAndAntiDerSltExists() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "c", "a", "b") };
		final String inputSTR = "(exists ((x (_ BitVec 8))) (and (bvslt x c) (distinct x b)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionAndAntiDerSleExists() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "a", "b") };
		final String inputSTR = "(exists ((x (_ BitVec 32))) (and (bvsle a x) (distinct b x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionAndAntiDerUltForall() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "c", "a", "b") };
		final String inputSTR = "(forall ((x (_ BitVec 8))) (or (bvult x c) (= x b)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionAndAntiDerUleForall() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "a", "b") };
		final String inputSTR = "(forall ((x (_ BitVec 32))) (or (bvule a x) (= b x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionAndAntiDerSltForall() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "c", "a", "b") };
		final String inputSTR = "(forall ((x (_ BitVec 8))) (or (bvslt x c) (= x b)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirSingleDirectionAndAntiDerSleForall() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "a", "b") };
		final String inputSTR = "(forall ((x (_ BitVec 32))) (or (bvsle a x) (= b x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirBug06LimitedDomain() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort1, "c") };
		final String inputSTR =
				"(exists ((x (_ BitVec 1)) (y (_ BitVec 1))) (and (not (= x y)) (not (= x c)) (not (= y c))))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirBug07() {
		final VarDecl[] funDecls =
				{ new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "main_~a~0", "main_~b~0") };
		final String inputSTR =
				"(forall ((|main_#t~post2| (_ BitVec 32)) (main_~i~0 (_ BitVec 32)) (|v_main_#t~post2_11| (_ BitVec 32)) (|v_main_#t~ret1_14| (_ BitVec 32)) (v_main_~b~0_19 (_ BitVec 32))) (or (bvult v_main_~b~0_19 main_~a~0) (bvult (bvadd (bvneg v_main_~b~0_19) main_~a~0) (_ bv1 32)) (not (bvult main_~i~0 main_~a~0)) (and (or (= |v_main_#t~ret1_14| (_ bv0 32)) (not (= (bvadd v_main_~b~0_19 (_ bv4294967295 32)) main_~b~0))) (or (not (= |v_main_#t~ret1_14| (_ bv0 32))) (not (= v_main_~b~0_19 main_~b~0)) (not (= |main_#t~post2| |v_main_#t~post2_11|))))))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirOffsetBug01() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort8, "a", "b") };
		final String inputSTR = "(forall ((x (_ BitVec 8))) (or (bvuge x a) (bvule x b)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirHospitalized() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "A", "q", "r") };
		final String inputSTR = "(forall ((B (_ BitVec 32))) (= A (bvadd (bvmul q B) r)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirIrd1() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "a", "b") };
		final String inputSTR = "(exists ((x (_ BitVec 32))) (and (bvslt a x) (distinct b x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void bvTirIrd3() {
		final VarDecl[] funDecls = { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "a", "b") };
		final String inputSTR = "(exists ((x (_ BitVec 32))) (bvult a x)))";
		final String expectedResult = inputSTR;
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}





	/**
	 * Regression test for bug in array PQE. Should maybe be moved to different file.
	 */
	@Test
	public void heap_data_calendar() {
		final VarDecl[] funDecls = new VarDecl[] {
				new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "main_~ev1~0", "main_~ev2~0"), };
		final String inputSTR =
				"(forall ((|#memory_int| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (v_main_~p~0.offset_6 (_ BitVec 32)) (v_main_~l~0.base_13 (_ BitVec 32)) (|v_main_#t~malloc5.offset_6| (_ BitVec 32)) (|v_#memory_int_19| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (v_main_~p~0.base_6 (_ BitVec 32)) (v_main_~l~0.offset_13 (_ BitVec 32))) (or (not (and (= v_main_~p~0.base_6 v_main_~l~0.base_13) (= |v_#memory_int_19| (store |#memory_int| v_main_~p~0.base_6 (store (store (store (select |#memory_int| v_main_~p~0.base_6) (bvadd v_main_~p~0.offset_6 (_ bv4 32)) main_~ev1~0) (bvadd v_main_~p~0.offset_6 (_ bv8 32)) main_~ev2~0) v_main_~p~0.offset_6 (select (select |v_#memory_int_19| v_main_~p~0.base_6) v_main_~p~0.offset_6)))) (= v_main_~l~0.offset_13 v_main_~p~0.offset_6) (or (not (= (_ bv3 32) main_~ev2~0)) (not (= (_ bv1 32) main_~ev1~0))) (= (_ bv0 32) |v_main_#t~malloc5.offset_6|) (= v_main_~p~0.offset_6 |v_main_#t~malloc5.offset_6|))) (forall ((x (_ BitVec 32)) (y (_ BitVec 32)) (v_DerPreprocessor_2 (_ BitVec 32)) (v_main_~p~0.base_5 (_ BitVec 32))) (or (not (= (bvadd (select (select (store |v_#memory_int_19| v_main_~p~0.base_5 (store (store (store (select |v_#memory_int_19| v_main_~p~0.base_5) (_ bv4 32) x) (_ bv8 32) y) (_ bv0 32) v_DerPreprocessor_2)) v_main_~l~0.base_13) (bvadd v_main_~l~0.offset_13 (_ bv8 32))) (_ bv4294967293 32)) (_ bv0 32))) (bvsgt x (_ bv3 32)) (= (_ bv3 32) y) (not (= (_ bv1 32) (select (store (store (store (select |v_#memory_int_19| v_main_~p~0.base_5) (_ bv4 32) x) (_ bv8 32) y) (_ bv0 32) v_DerPreprocessor_2) (_ bv4 32)))) (bvslt x (_ bv0 32)) (not (= (_ bv0 32) (bvadd (select (select (store |v_#memory_int_19| v_main_~p~0.base_5 (store (store (store (select |v_#memory_int_19| v_main_~p~0.base_5) (_ bv4 32) x) (_ bv8 32) y) (_ bv0 32) v_DerPreprocessor_2)) v_main_~l~0.base_13) (bvadd v_main_~l~0.offset_13 (_ bv4 32))) (_ bv4294967295 32))))))))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	/**
	 * Regression test for bug in array PQE. Should maybe be moved to different file.
	 */
	@Test
	public void heap_data_cart() {
		final VarDecl[] funDecls =
				new VarDecl[] { new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "idxDim1", "idxDim2"),
						new VarDecl(QuantifierEliminationTest::getArrayBv32Bv32Bv32Sort, "arr"), };
		final String inputSTR =
				"(and (= idxDim2 (_ bv0 32)) (exists ((x (_ BitVec 32))) (and (exists ((y Bool) (|â| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32))))) (and (not y) (or (and (not (bvslt (select (select |â| idxDim1) (bvadd idxDim2 (_ bv4 32))) x)) (not y)) (and y (bvslt (select (select |â| idxDim1) (bvadd idxDim2 (_ bv4 32))) x))) (= (select (select |â| idxDim1) (bvadd idxDim2 (_ bv8 32))) (_ bv0 32)) (= arr (store |â| idxDim1 (store (store (select |â| idxDim1) (bvadd idxDim2 (_ bv8 32)) x) (bvadd idxDim2 (_ bv4 32)) (select (store (select |â| idxDim1) (bvadd idxDim2 (_ bv8 32)) x) (bvadd idxDim2 (_ bv4 32)))))))) (not (bvslt x (_ bv0 32))))))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	// @Test // TODO no termination?
	public void list_simple_dll2cupdateall() {
		final VarDecl[] funDecls = new VarDecl[] {
				new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "main_~s~0.base", "main_~s~0.offset",
						"main_~new_data~0", "main_~len~0"),
				new VarDecl(QuantifierEliminationTest::getArrayBv32Bv32Bv32Sort, "#memory_$Pointer$.base"), };
		final String inputSTR =
				"(forall ((|v_dll_circular_update_at_#in~head.offset_11| (_ BitVec 32)) (|v_#memory_int_114| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (v_dll_circular_update_at_~head.offset_21 (_ BitVec 32)) (|v_#memory_int_115| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (|v_#memory_$Pointer$.base_115| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (v_dll_circular_update_at_~data_16 (_ BitVec 32)) (|v_dll_circular_update_at_#in~head.base_11| (_ BitVec 32)) (v_dll_circular_update_at_~head.base_21 (_ BitVec 32)) (|v_dll_circular_update_at_#in~data_10| (_ BitVec 32)) (|v_old(#memory_$Pointer$.base)_40| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32))))) (or (forall ((v_dll_circular_update_at_~head.offset_16 (_ BitVec 32)) (v_dll_circular_update_at_~data_12 (_ BitVec 32))) (= (select (select (store |v_#memory_int_115| (select (select |v_#memory_$Pointer$.base_115| main_~s~0.base) main_~s~0.offset) (store (select |v_#memory_int_115| (select (select |v_#memory_$Pointer$.base_115| main_~s~0.base) main_~s~0.offset)) (bvadd v_dll_circular_update_at_~head.offset_16 (_ bv8 32)) v_dll_circular_update_at_~data_12)) main_~s~0.base) (bvadd main_~s~0.offset (_ bv8 32))) main_~len~0)) (not (and (= v_dll_circular_update_at_~data_16 |v_dll_circular_update_at_#in~data_10|) (= |v_#memory_int_115| (store |v_#memory_int_114| v_dll_circular_update_at_~head.base_21 (store (select |v_#memory_int_114| v_dll_circular_update_at_~head.base_21) (bvadd v_dll_circular_update_at_~head.offset_21 (_ bv8 32)) v_dll_circular_update_at_~data_16))) (= main_~s~0.base |v_dll_circular_update_at_#in~head.base_11|) (= (store |v_old(#memory_$Pointer$.base)_40| v_dll_circular_update_at_~head.base_21 (store (select |v_old(#memory_$Pointer$.base)_40| v_dll_circular_update_at_~head.base_21) (bvadd v_dll_circular_update_at_~head.offset_21 (_ bv8 32)) (select (select |v_#memory_$Pointer$.base_115| v_dll_circular_update_at_~head.base_21) (bvadd v_dll_circular_update_at_~head.offset_21 (_ bv8 32))))) |v_#memory_$Pointer$.base_115|) (= |v_dll_circular_update_at_#in~head.base_11| v_dll_circular_update_at_~head.base_21) (= |v_dll_circular_update_at_#in~head.offset_11| main_~s~0.offset) (= main_~new_data~0 |v_dll_circular_update_at_#in~data_10|) (= |v_old(#memory_$Pointer$.base)_40| |#memory_$Pointer$.base|) (= |v_dll_circular_update_at_#in~head.offset_11| v_dll_circular_update_at_~head.offset_21)))))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}

	@Test
	public void forester_heap_dll_optional() {
		final VarDecl[] funDecls = new VarDecl[] {
				new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "main_~head~0.offset", "main_~head~0.base"),
				new VarDecl(QuantifierEliminationTest::getArrayBv32Bv1Sort, "#valid"), };
		final String inputSTR =
				"(forall ((|#memory_INTTYPE4| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (v_DerPreprocessor_10 (_ BitVec 32)) (v_DerPreprocessor_9 (_ BitVec 32)) (|v_main_#t~malloc6.base_4| (_ BitVec 32)) (|main_#t~mem7.offset| (_ BitVec 32)) (v_DerPreprocessor_8 (_ BitVec 32)) (v_subst_6 (_ BitVec 32)) (v_DerPreprocessor_7 (_ BitVec 32)) (v_DerPreprocessor_6 (_ BitVec 32))) (or (not (= (select |#valid| |v_main_#t~malloc6.base_4|) (_ bv0 1))) (not (= (select (select (store (store |#memory_INTTYPE4| main_~head~0.base (store (store (store (select |#memory_INTTYPE4| main_~head~0.base) (bvadd main_~head~0.offset (_ bv12 32)) (_ bv0 32)) (bvadd main_~head~0.offset (_ bv8 32)) v_DerPreprocessor_10) main_~head~0.offset v_DerPreprocessor_9)) |v_main_#t~malloc6.base_4| (store (store (store (store (select (store |#memory_INTTYPE4| main_~head~0.base (store (store (store (select |#memory_INTTYPE4| main_~head~0.base) (bvadd main_~head~0.offset (_ bv12 32)) (_ bv0 32)) (bvadd main_~head~0.offset (_ bv8 32)) v_DerPreprocessor_10) main_~head~0.offset v_DerPreprocessor_9)) |v_main_#t~malloc6.base_4|) (bvadd |main_#t~mem7.offset| (_ bv4 32)) v_DerPreprocessor_8) v_subst_6 v_DerPreprocessor_7) (bvadd v_subst_6 (_ bv12 32)) (_ bv0 32)) (bvadd v_subst_6 (_ bv8 32)) v_DerPreprocessor_6)) main_~head~0.base) (bvadd main_~head~0.offset (_ bv12 32))) (_ bv2 32)))))";
		final String expectedResultAsString =
				"(forall ((v_subst_6 (_ BitVec 32)) (v_DerPreprocessor_8 (_ BitVec 32)) (|main_#t~mem7.offset| (_ BitVec 32)) (v_DerPreprocessor_7 (_ BitVec 32)) (|v_main_#t~malloc6.base_4| (_ BitVec 32)) (v_DerPreprocessor_6 (_ BitVec 32))) (or (not (= (select |#valid| |v_main_#t~malloc6.base_4|) (_ bv0 1))) (not (and (or (and (not (= (bvadd main_~head~0.offset (_ bv12 32)) (bvadd v_subst_6 (_ bv8 32)))) (= main_~head~0.base |v_main_#t~malloc6.base_4|) (or (and (= (bvadd main_~head~0.offset (_ bv12 32)) v_subst_6) (= (_ bv2 32) v_DerPreprocessor_7)) (and (not (= (bvadd main_~head~0.offset (_ bv12 32)) v_subst_6)) (= (_ bv2 32) v_DerPreprocessor_8) (= (bvadd main_~head~0.offset (_ bv12 32)) (bvadd |main_#t~mem7.offset| (_ bv4 32)))))) (and (= (bvadd main_~head~0.offset (_ bv12 32)) (bvadd v_subst_6 (_ bv8 32))) (= (_ bv2 32) v_DerPreprocessor_6) (= main_~head~0.base |v_main_#t~malloc6.base_4|))) (not (= (bvadd v_subst_6 (_ bv12 32)) (bvadd main_~head~0.offset (_ bv12 32))))))))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
	}


	@Test
	public void sll_circular_traversal_2() {
		final VarDecl[] funDecls = new VarDecl[] {
				new VarDecl(QuantifierEliminationTest::getBitvectorSort32, "sll_circular_create_~new_head~0.base",
						"sll_circular_create_~new_head~0.offset", "sll_circular_create_~head~0.offset",
						"sll_circular_create_~head~0.base", "sll_circular_create_~last~0.base",
						"sll_circular_create_~last~0.offset"),
				new VarDecl(QuantifierEliminationTest::getArrayBv32Bv32Sort, "#length"), };
		final String inputSTR =
				"(forall ((|#memory_$Pointer$.base| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (|#memory_$Pointer$.offset| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (|v_#memory_$Pointer$.offset_156| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32)))) (|v_#memory_$Pointer$.base_164| (Array (_ BitVec 32) (Array (_ BitVec 32) (_ BitVec 32))))) (or (and (= sll_circular_create_~new_head~0.base (select (select (store |v_#memory_$Pointer$.base_164| sll_circular_create_~last~0.base (store (select |v_#memory_$Pointer$.base_164| sll_circular_create_~last~0.base) sll_circular_create_~last~0.offset sll_circular_create_~new_head~0.base)) sll_circular_create_~new_head~0.base) sll_circular_create_~new_head~0.offset)) (= (select (select (store |v_#memory_$Pointer$.offset_156| sll_circular_create_~last~0.base (store (select |v_#memory_$Pointer$.offset_156| sll_circular_create_~last~0.base) sll_circular_create_~last~0.offset sll_circular_create_~new_head~0.offset)) sll_circular_create_~new_head~0.base) sll_circular_create_~new_head~0.offset) sll_circular_create_~new_head~0.offset)) (not (and (= (store |#memory_$Pointer$.offset| sll_circular_create_~new_head~0.base (store (select |#memory_$Pointer$.offset| sll_circular_create_~new_head~0.base) sll_circular_create_~new_head~0.offset sll_circular_create_~head~0.offset)) |v_#memory_$Pointer$.offset_156|) (= (store |#memory_$Pointer$.base| sll_circular_create_~new_head~0.base (store (select |#memory_$Pointer$.base| sll_circular_create_~new_head~0.base) sll_circular_create_~new_head~0.offset sll_circular_create_~head~0.base)) |v_#memory_$Pointer$.base_164|))) (and (bvule (bvadd (select (select (store |v_#memory_$Pointer$.offset_156| sll_circular_create_~last~0.base (store (select |v_#memory_$Pointer$.offset_156| sll_circular_create_~last~0.base) sll_circular_create_~last~0.offset sll_circular_create_~new_head~0.offset)) sll_circular_create_~new_head~0.base) sll_circular_create_~new_head~0.offset) (_ bv8 32)) (select |#length| (select (select (store |v_#memory_$Pointer$.base_164| sll_circular_create_~last~0.base (store (select |v_#memory_$Pointer$.base_164| sll_circular_create_~last~0.base) sll_circular_create_~last~0.offset sll_circular_create_~new_head~0.base)) sll_circular_create_~new_head~0.base) sll_circular_create_~new_head~0.offset))) (bvule (bvadd (select (select (store |v_#memory_$Pointer$.offset_156| sll_circular_create_~last~0.base (store (select |v_#memory_$Pointer$.offset_156| sll_circular_create_~last~0.base) sll_circular_create_~last~0.offset sll_circular_create_~new_head~0.offset)) sll_circular_create_~new_head~0.base) sll_circular_create_~new_head~0.offset) (_ bv4 32)) (bvadd (select (select (store |v_#memory_$Pointer$.offset_156| sll_circular_create_~last~0.base (store (select |v_#memory_$Pointer$.offset_156| sll_circular_create_~last~0.base) sll_circular_create_~last~0.offset sll_circular_create_~new_head~0.offset)) sll_circular_create_~new_head~0.base) sll_circular_create_~new_head~0.offset) (_ bv8 32))))))";
		setUpScript(SOLVER_COMMAND_Z3, funDecls);
		final Term qelim = translateQelimBacktranslate(parse(inputSTR));
		testQelim(parse(inputSTR), qelim);
	}
}