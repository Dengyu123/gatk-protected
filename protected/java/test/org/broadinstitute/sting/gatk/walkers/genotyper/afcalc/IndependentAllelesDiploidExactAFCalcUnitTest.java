package org.broadinstitute.sting.gatk.walkers.genotyper.afcalc;

import org.broadinstitute.sting.BaseTest;
import org.broadinstitute.sting.utils.MathUtils;
import org.broadinstitute.sting.utils.Utils;
import org.broadinstitute.sting.utils.variantcontext.Allele;
import org.broadinstitute.sting.utils.variantcontext.Genotype;
import org.broadinstitute.sting.utils.variantcontext.VariantContext;
import org.broadinstitute.sting.utils.variantcontext.VariantContextBuilder;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;


// SEE  private/R/pls.R if you want the truth output for these tests
public class IndependentAllelesDiploidExactAFCalcUnitTest extends BaseTest {
    @DataProvider(name = "TestCombineGLs")
    public Object[][] makeTestCombineGLs() {
        List<Object[]> tests = new ArrayList<Object[]>();

        tests.add(new Object[]{1, 1, makePL( 0, 10, 20), makePL( 0, 10, 20)});
        tests.add(new Object[]{1, 1, makePL(10,  0, 20), makePL(10,  0, 20)});
        tests.add(new Object[]{1, 1, makePL(20, 10,  0), makePL(20, 10,  0)});

        // AA AB BB AC BC CC => AA AB+BC CC
        tests.add(new Object[]{1, 2, makePL( 0, 10, 20, 30, 40, 50), makePL(0, 10, 20)});
        tests.add(new Object[]{2, 2, makePL( 0, 10, 20, 30, 40, 50), makePL(0, 30, 50)});

        tests.add(new Object[]{1, 2, makePL( 0, 10, 10, 10, 10, 10), makePL(0, 8, 11)});
        tests.add(new Object[]{2, 2, makePL( 0, 10, 10, 10, 10, 10), makePL(0, 8, 11)});

        tests.add(new Object[]{1, 2, makePL( 0, 1, 2, 3, 4, 5), makePL(0, 2, 5)});
        tests.add(new Object[]{2, 2, makePL( 0, 1, 2, 3, 4, 5), makePL(0, 4, 9)});

        tests.add(new Object[]{1, 2, makePL(  0, 50, 50, 50, 50, 50), makePL( 0, 47, 50)});
        tests.add(new Object[]{2, 2, makePL(  0, 50, 50, 50, 50, 50), makePL( 0, 47, 50)});

        tests.add(new Object[]{1, 2, makePL( 50,  0, 50, 50, 50, 50), makePL(45, 0, 50)});
        tests.add(new Object[]{2, 2, makePL( 50,  0, 50, 50, 50, 50), makePL( 0, 47, 50)});

        tests.add(new Object[]{1, 2, makePL( 50, 50, 0, 50, 50, 50), makePL(45, 47,  0)});
        tests.add(new Object[]{2, 2, makePL( 50, 50, 0, 50, 50, 50), makePL( 0, 47, 50)});

        tests.add(new Object[]{1, 2, makePL( 50, 50, 50,  0, 50, 50), makePL(0, 47, 50)});
        tests.add(new Object[]{2, 2, makePL( 50, 50, 50,  0, 50, 50), makePL(45, 0, 50)});

        tests.add(new Object[]{1, 2, makePL( 50, 50, 50, 50, 0, 50), makePL(45, 0, 50)});
        tests.add(new Object[]{2, 2, makePL( 50, 50, 50, 50, 0, 50), makePL(45, 0, 50)});

        tests.add(new Object[]{1, 2, makePL( 50, 50, 50, 50, 50,  0), makePL(0, 47, 50)});
        tests.add(new Object[]{2, 2, makePL( 50, 50, 50, 50, 50,  0), makePL(45, 47, 0)});

        return tests.toArray(new Object[][]{});
    }

    private Genotype makePL(final int ... PLs) {
        return AFCalcUnitTest.makePL(Arrays.asList(Allele.NO_CALL, Allele.NO_CALL), PLs);
    }

    @Test(enabled = true, dataProvider = "TestCombineGLs")
    private void testCombineGLs(final int altIndex, final int nAlts, final Genotype testg, final Genotype expected) {
        final IndependentAllelesDiploidExactAFCalc calc = (IndependentAllelesDiploidExactAFCalc)AFCalcFactory.createAFCalc(AFCalcFactory.Calculation.EXACT_INDEPENDENT, 1, 4);
        final Genotype combined = calc.combineGLs(testg, altIndex, nAlts);

        Assert.assertEquals(combined.getPL(), expected.getPL(),
                "Combined PLs " + Utils.join(",", combined.getPL()) + " != expected " + Utils.join(",", expected.getPL()));
    }


    static Allele A = Allele.create("A", true);
    static Allele C = Allele.create("C");
    static Allele G = Allele.create("G");

    @DataProvider(name = "TestMakeAlleleConditionalContexts")
    public Object[][] makeTestMakeAlleleConditionalContexts() {
        List<Object[]> tests = new ArrayList<Object[]>();

        final VariantContextBuilder root = new VariantContextBuilder("x", "1", 1, 1, Arrays.asList(A));
        final VariantContextBuilder vcAC = new VariantContextBuilder(root).alleles(Arrays.asList(A, C));
        final VariantContextBuilder vcAG = new VariantContextBuilder(root).alleles(Arrays.asList(A, G));
        final VariantContextBuilder vcACG = new VariantContextBuilder(root).alleles(Arrays.asList(A, C, G));
        final VariantContextBuilder vcAGC = new VariantContextBuilder(root).alleles(Arrays.asList(A, G, C));

        final Genotype gACG = makePL( 0, 1, 2, 3, 4, 5);
        final Genotype gAGC = makePL( 0, 4, 5, 1, 3, 2);
        final Genotype gACcombined = makePL(0, 2, 5);
        final Genotype gACcombined2 = makePL(0, 1, 4);
        final Genotype gAGcombined = makePL(0, 4, 9);

        // biallelic
        tests.add(new Object[]{vcAC.genotypes(gACcombined).make(), Arrays.asList(vcAC.genotypes(gACcombined).make())});

        // tri-allelic
        tests.add(new Object[]{vcACG.genotypes(gACG).make(), Arrays.asList(vcAC.genotypes(gACcombined).make(), vcAG.genotypes(gAGcombined).make())});
        tests.add(new Object[]{vcAGC.genotypes(gAGC).make(), Arrays.asList(vcAG.genotypes(gAGcombined).make(), vcAC.genotypes(gACcombined2).make())});

        return tests.toArray(new Object[][]{});
    }


    @Test(enabled = true, dataProvider = "TestMakeAlleleConditionalContexts")
    private void testMakeAlleleConditionalContexts(final VariantContext vc, final List<VariantContext> expectedVCs) {
        final IndependentAllelesDiploidExactAFCalc calc = (IndependentAllelesDiploidExactAFCalc)AFCalcFactory.createAFCalc(AFCalcFactory.Calculation.EXACT_INDEPENDENT, 1, 4);
        final List<VariantContext> biAllelicVCs = calc.makeAlleleConditionalContexts(vc);

        Assert.assertEquals(biAllelicVCs.size(), expectedVCs.size());

        for ( int i = 0; i < biAllelicVCs.size(); i++ ) {
            final VariantContext actual = biAllelicVCs.get(i);
            final VariantContext expected = expectedVCs.get(i);
            Assert.assertEquals(actual.getAlleles(), expected.getAlleles());

            for ( int j = 0; j < actual.getNSamples(); j++ )
                Assert.assertEquals(actual.getGenotype(j).getPL(), expected.getGenotype(j).getPL(),
                        "expected PLs " + Utils.join(",", expected.getGenotype(j).getPL()) + " not equal to actual " + Utils.join(",", actual.getGenotype(j).getPL()));
        }
    }


    @DataProvider(name = "ThetaNTests")
    public Object[][] makeThetaNTests() {
        List<Object[]> tests = new ArrayList<Object[]>();

        final List<Double> log10LAlleles = Arrays.asList(0.0, -1.0, -2.0, -3.0, -4.0);

        for ( final double log10pRef : Arrays.asList(-1, -2, -3) ) {
            for ( final int ploidy : Arrays.asList(1, 2, 3, 4) ) {
                for ( List<Double> permutations : Utils.makePermutations(log10LAlleles, ploidy, true)) {
                    tests.add(new Object[]{permutations, Math.pow(10, log10pRef)});
                }
            }
        }

        return tests.toArray(new Object[][]{});
    }

    @Test(dataProvider = "ThetaNTests")
    public void testThetaNTests(final List<Double> log10LAlleles, final double pRef) {
        // biallelic
        final double[] rawPriors = MathUtils.toLog10(new double[]{pRef, 1-pRef});

        final double log10pNonRef = Math.log10(1-pRef);

        final List<AFCalcResult> originalPriors = new LinkedList<AFCalcResult>();
        final List<Double> pNonRefN = new LinkedList<Double>();
        for ( int i = 0; i < log10LAlleles.size(); i++ ) {
            final double log10LAllele1 = log10LAlleles.get(i);
            final double[] L1 = MathUtils.normalizeFromLog10(new double[]{log10LAllele1, 0.0}, true);
            final AFCalcResult result1 = new AFCalcResult(new int[]{1}, 1, Arrays.asList(A, C), L1, rawPriors, Collections.singletonMap(C, -10000.0));
            originalPriors.add(result1);
            pNonRefN.add(log10pNonRef*(i+1));
        }

        final IndependentAllelesDiploidExactAFCalc calc = (IndependentAllelesDiploidExactAFCalc)AFCalcFactory.createAFCalc(AFCalcFactory.Calculation.EXACT_INDEPENDENT, 1, 2);
        final List<AFCalcResult> thetaNPriors = calc.applyMultiAllelicPriors(originalPriors);

        double prevPosterior = 0.0;
        for ( int i = 0; i < log10LAlleles.size(); i++ ) {
            final AFCalcResult thetaN = thetaNPriors.get(i);
            AFCalcResult orig = null;
            for ( final AFCalcResult x : originalPriors )
                if ( x.getAllelesUsedInGenotyping().equals(thetaN.getAllelesUsedInGenotyping()))
                    orig = x;

            Assert.assertNotNull(orig, "couldn't find original AFCalc");

            Assert.assertEquals(orig.getLog10PriorOfAFGT0(), log10pNonRef, 1e-6);
            Assert.assertEquals(thetaN.getLog10PriorOfAFGT0(), pNonRefN.get(i), 1e-6);

            Assert.assertTrue(orig.getLog10PosteriorOfAFGT0() <= prevPosterior, "AFCalc results should be sorted but " + prevPosterior + " is > original posterior " + orig.getLog10PosteriorOfAFGT0());
            prevPosterior = orig.getLog10PosteriorOfAFGT0();
        }
    }
}