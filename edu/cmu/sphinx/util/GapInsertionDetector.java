/*
 * Copyright 1999-2002 Carnegie Mellon University.
 * Portions Copyright 2002 Sun Microsystems, Inc.
 * Portions Copyright 2002 Mitsubishi Electronic Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 */
package edu.cmu.sphinx.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.StringTokenizer;


/**
 * A program that takes in a reference transcript and a hypothesis transcript
 * and figures out how many gap insertion errors are there.
 * The hypothesis transcript file should contain timestamps for when
 * each word was entered and exited.
 * <p>The gap insertion detection algorithm works as follows. It takes each
 * hypothesized word individually and see whether it falls into a non-speech
 * region in the reference transcript. If it does, that hypothesized word
 * is counted as a gap insertion.
 */
public class GapInsertionDetector {

    private ReferenceFile referenceFile;
    private HypothesisFile hypothesisFile;
    private int totalGapInsertions;

    public GapInsertionDetector(String referenceFile, String hypothesisFile)
        throws IOException {
        this.referenceFile = new ReferenceFile(referenceFile);
        this.hypothesisFile = new HypothesisFile(hypothesisFile);
    }

    /**
     * Detect the gap insertion errors.
     *
     * @return the total number of gap insertion errors
     */
    public int detect() throws IOException {
        int gaps = 0;
        boolean done = false;
        ReferenceUtterance reference = referenceFile.nextUtterance();
        String log = "";
        while (!done) {
            HypothesisWord word = hypothesisFile.nextWord();
            if (word != null) {
                boolean hasGapError = false;

                // go to the relevant reference utterance
                while (reference != null &&
                       reference.getEndTime() < word.getStartTime()) {
                    reference = referenceFile.nextUtterance();
                }
                
                // 'reference' should be the relevant one now
                if (reference != null) {
                    if (reference.isSilenceGap()) {
                        hasGapError = true;
                    } else {
                        while (reference.getEndTime() < word.getEndTime()) {
                            reference = referenceFile.nextUtterance();
                            if (reference == null ||
                                reference.isSilenceGap()) {
                                hasGapError = true;
                                break;
                            }
                        }
                    }
                } else {
                    // if no more reference words, this is a gap insertion
                    hasGapError = true;
                }

                if (hasGapError) {
                    gaps++;
                    log += "GapInsError: Utterance: " + 
                        hypothesisFile.getUtteranceCount() + 
                        " Word: " + word.getText() + " (" + 
                        word.getStartTime() + "," + word.getEndTime() + "). ";
                    if (reference != null) {
                        assert reference.isSilenceGap();
                        log += ("Reference: <sil> (" + 
                                reference.getStartTime() + "," +
                                reference.getEndTime() + ")");
                    }
                    log += "\n";
                }
            } else {
                done = true;
            }
        }
        totalGapInsertions += gaps;
        System.out.println(log);
        return gaps;
    }
}

/**
 * Creates a ReferenceFile.
 */
class ReferenceFile {

    private BufferedReader reader;

    /**
     * Creates a ReferenceFile, given the name of the reference file.
     *
     * @param the name of the reference file
     */
    ReferenceFile(String fileName) throws IOException {
        reader = new BufferedReader(new FileReader(fileName));
    }

    /**
     * Returns the next available ReferenceUtterance. This method
     * skips all the silence gaps.
     *
     * @return the next available ReferenceUtterance, or null if the
     *    end of file has been reached.
     */
    ReferenceUtterance nextUtterance() throws IOException {
        String line = reader.readLine();
        if (line != null) {
            return new ReferenceUtterance(line);
        } else {
            return null;
        }
    }
}

/**
 * Converts a line in the HUB-4 .stm reference file into an object.
 */
class ReferenceUtterance {

    private boolean isSilenceGap;
    private float startTime;
    private float endTime;
    private String[] words;

    ReferenceUtterance(String line) {
        StringTokenizer st = new StringTokenizer(line);
        st.nextToken();                 // parse the test set name
        st.nextToken();                 // parse category
        String type = st.nextToken();   // parse speaker
        if (type.equals("inter_segment_gap")) {
            isSilenceGap = true;
        }
        startTime = Float.parseFloat(st.nextToken()); // parse start time
        endTime = Float.parseFloat(st.nextToken());   // parse end time
        st.nextToken();                               // parse <...>
        words = new String[st.countTokens()];
        for (int i = 0; i < words.length; i++) {
            words[i] = st.nextToken();
        }
    }

    /**
     * Returns true if this is a silence gap.
     *
     * @return true if this is a silence gap, false otherwise.
     */
    boolean isSilenceGap() {
        return isSilenceGap;
    }

    /**
     * Returns the starting time (in seconds) of this utterance.
     *
     * @return the starting time of this utterance
     */
    float getStartTime() {
        return startTime;
    }

    /**
     * Returns the ending time (in seconds) of this utterance.
     *
     * @return the ending time of this utterance
     */
    float getEndTime() {
        return endTime;
    }

    /**
     * Returns the text of this utterance.
     *
     * @return the text of this utterance
     */
    String[] getWords() {
        return words;
    }
}

class HypothesisFile {
    
    private BufferedReader reader;
    private Iterator iterator;
    private int utteranceCount = 0;

    /**
     * Creates a HypothesisFile from the given file.
     *
     * @param fileName the name of the hypothesis file
     */
    HypothesisFile(String fileName) throws IOException {
        reader = new BufferedReader(new FileReader(fileName));
    }

    HypothesisWord nextWord() throws IOException {
        if (iterator == null || !iterator.hasNext()) {
            HypothesisUtterance utterance = nextUtterance();
            if (utterance != null) {
                iterator = utterance.getWords().iterator();
            } else {
                iterator = null;
            }
        }
        if (iterator == null) {
            return null;
        } else {
            return (HypothesisWord) iterator.next();
        }
    }

    /**
     * Returns the next available hypothesis utterance.
     *
     * @return the next available hypothesis utterance, or null if 
     *    the end of file has been reached
     */
    private HypothesisUtterance nextUtterance() throws IOException {
        String line = reader.readLine();
        if (line != null) {
            utteranceCount++;
            return new HypothesisUtterance(line);
        } else {
            return null;
        }
    }

    /**
     * Returns the utterance count.
     *
     * @return the utterance count
     */
    public int getUtteranceCount() {
        return utteranceCount;
    }
}

class HypothesisUtterance {

    private List words;
    private float startTime;
    private float endTime;

    HypothesisUtterance(String line) {
        words = new LinkedList();
        StringTokenizer st = new StringTokenizer(line, " \t\n\r\f(),");
        if (!st.hasMoreTokens()) {
            throw new Error("Utterance has no words");
        }
        while (st.hasMoreTokens()) {
            String text = st.nextToken();
            float myStartTime = Float.parseFloat(st.nextToken());
            float myEndTime = Float.parseFloat(st.nextToken());
            HypothesisWord word = new HypothesisWord
                (text, myStartTime, myEndTime);
            words.add(word);
        }
        HypothesisWord firstWord = (HypothesisWord) words.get(0);
        startTime = firstWord.getStartTime();
        HypothesisWord lastWord = (HypothesisWord) words.get(words.size()-1);
        endTime = lastWord.getEndTime();
    }

    int getWordCount() {
        return words.size();
    }

    List getWords() {
        List newList = new LinkedList();
        newList.addAll(words);
        return newList;
    }

    float getStartTime() {
        return startTime;
    }

    float getEndTime() {
        return endTime;
    }
}

class HypothesisWord {

    private String text;
    private float startTime;
    private float endTime;

    HypothesisWord(String text, float startTime, float endTime) {
        this.text = text;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    String getText() {
        return text;
    }

    float getStartTime() {
        return startTime;
    }

    float getEndTime() {
        return endTime;
    }
}