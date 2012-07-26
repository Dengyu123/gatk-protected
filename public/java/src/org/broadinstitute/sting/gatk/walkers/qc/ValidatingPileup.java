/*
 * Copyright (c) 2010 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.broadinstitute.sting.gatk.walkers.qc;

import org.broadinstitute.sting.commandline.Argument;
import org.broadinstitute.sting.commandline.Input;
import org.broadinstitute.sting.commandline.Output;
import org.broadinstitute.sting.commandline.RodBinding;
import org.broadinstitute.sting.gatk.CommandLineGATK;
import org.broadinstitute.sting.gatk.contexts.AlignmentContext;
import org.broadinstitute.sting.gatk.contexts.ReferenceContext;
import org.broadinstitute.sting.gatk.refdata.RefMetaDataTracker;
import org.broadinstitute.sting.gatk.walkers.DataSource;
import org.broadinstitute.sting.gatk.walkers.LocusWalker;
import org.broadinstitute.sting.gatk.walkers.Requires;
import org.broadinstitute.sting.gatk.walkers.TreeReducible;
import org.broadinstitute.sting.utils.GenomeLoc;
import org.broadinstitute.sting.utils.codecs.sampileup.SAMPileupFeature;
import org.broadinstitute.sting.utils.exceptions.ReviewedStingException;
import org.broadinstitute.sting.utils.exceptions.UserException;
import org.broadinstitute.sting.utils.help.DocumentedGATKFeature;
import org.broadinstitute.sting.utils.pileup.ReadBackedPileup;

import java.io.PrintStream;
import java.util.Arrays;

/**
 * At every locus in the input set, compares the pileup data (reference base, aligned base from
 * each overlapping read, and quality score) to the reference pileup data generated by samtools.  Samtools' pileup data
 * should be specified using the command-line argument '-pileup:SAMPileup <your sam pileup file>'.
 */
@DocumentedGATKFeature( groupName = "Quality Control and Simple Analysis Tools", extraDocs = {CommandLineGATK.class} )
@Requires(value={DataSource.READS,DataSource.REFERENCE})
public class ValidatingPileup extends LocusWalker<Integer, ValidationStats> implements TreeReducible<ValidationStats> {
    @Input(fullName = "pileup", doc="The SAMPileup containing the expected output", required = true)
    RodBinding<SAMPileupFeature> pileup;

    @Output
    private PrintStream out;

    @Argument(fullName="continue_after_error",doc="Continue after an error",required=false)
    public boolean CONTINUE_AFTER_AN_ERROR = false;

    public Integer map(RefMetaDataTracker tracker, ReferenceContext ref, AlignmentContext context) {
        ReadBackedPileup pileup = context.getBasePileup();
        SAMPileupFeature truePileup = getTruePileup( tracker );

        if ( truePileup == null ) {
            out.printf("No truth pileup data available at %s%n", pileup.getPileupString(ref.getBaseAsChar()));
            if ( ! CONTINUE_AFTER_AN_ERROR ) {
                throw new UserException.CommandLineException(String.format("No pileup data available at %s given GATK's output of %s -- this walker requires samtools pileup data over all bases",
                        context.getLocation(), new String(pileup.getBases())));
            }
        } else {
            String pileupDiff = pileupDiff(pileup, truePileup, true);
            if ( pileupDiff != null ) {
                out.printf("%s vs. %s%n", pileup.getPileupString(ref.getBaseAsChar()), truePileup.getPileupString());
                if ( ! CONTINUE_AFTER_AN_ERROR ) {
                    throw new RuntimeException(String.format("Pileups aren't equal: %s", pileupDiff));
                }
            }
        }

        return pileup.getNumberOfElements();
    }

    private static String maybeSorted( final String x, boolean sortMe )
    {
        if ( sortMe ) {
            byte[] bytes = x.getBytes();
            Arrays.sort(bytes);
            return new String(bytes);
        }
        else
            return x;
    }

    public String pileupDiff(final ReadBackedPileup a, final SAMPileupFeature b, boolean orderDependent)
    {
        if ( a.getNumberOfElements() != b.size() )
            return "Sizes not equal";
        GenomeLoc featureLocation = getToolkit().getGenomeLocParser().createGenomeLoc(b.getChr(),b.getStart(),b.getEnd());
        if ( a.getLocation().compareTo(featureLocation) != 0 )
            return "Locations not equal";

        String aBases = maybeSorted(new String(a.getBases()), ! orderDependent );
        String bBases = maybeSorted(b.getBasesAsString(), ! orderDependent );
        if ( ! aBases.toUpperCase().equals(bBases.toUpperCase()) )
            return "Bases not equal";

        String aQuals = maybeSorted(new String(a.getQuals()), ! orderDependent );
        String bQuals = maybeSorted(new String(b.getQuals()), ! orderDependent );
        if ( ! aQuals.equals(bQuals) )
            return "Quals not equal";

        return null;
    }

    // Given result of map function
    public ValidationStats reduceInit() { return new ValidationStats(); }
    public ValidationStats reduce(Integer value, ValidationStats sum) {
        sum.nLoci++;
        sum.nBases += value;
        return sum;
    }

    public ValidationStats treeReduce( ValidationStats lhs, ValidationStats rhs ) {
        ValidationStats combined = new ValidationStats();
        combined.nLoci = lhs.nLoci + rhs.nLoci;
        combined.nBases = lhs.nBases + rhs.nBases;
        return combined;
    }

    /**
     * Extracts the true pileup data from the given rodSAMPileup.  Note that this implementation
     * assumes that the genotype will only be point or indel.
     * @param tracker ROD tracker from which to extract pileup data.
     * @return True pileup data.
     */
    private SAMPileupFeature getTruePileup( RefMetaDataTracker tracker ) {
        SAMPileupFeature pileupArg = tracker.getFirstValue(pileup);

        if( pileupArg == null)
            return null;

        if( pileupArg.hasPointGenotype() )
            return pileupArg.getPointGenotype();
        else if( pileupArg.hasIndelGenotype() )
            return pileupArg.getIndelGenotype();
        else
            throw new ReviewedStingException("Unsupported pileup type: " + pileupArg);
    }
}

class ValidationStats {
    public long nLoci = 0;
    public long nBases = 0;

    public ValidationStats() {
    }

    public String toString() {
        return String.format("Validated %d sites covered by %d bases%n", nLoci, nBases);
    }
}