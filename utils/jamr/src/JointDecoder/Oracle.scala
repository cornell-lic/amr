package edu.cmu.lti.nlp.amr.JointDecoder
import edu.cmu.lti.nlp.amr._
import edu.cmu.lti.nlp.amr.FastFeatureVector._

import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer

class Oracle(stage1FeatureNames: List[String],
             phraseConceptPairs: Array[ConceptInvoke.PhraseConceptPair],  // this is the concept table
             stage2FeatureNames: List[String],
             labelSet: Array[(String, Int)]) extends Decoder {

    val weights = FeatureVector(labelSet.map(_._1))
    val stage1Features = new ConceptInvoke.Features(stage1FeatureNames)
    val stage2Features = new GraphDecoder.Features(stage2FeatureNames, weights.labelset)

    val conceptInvoker = new ConceptInvoke.Concepts(phraseConceptPairs)

    def decode(input: Input) : FastFeatureVector.DecoderResult = {
        stage2Features.input = input
        stage2Features.weights = weights
        assert(input.graph != None, "Error: oracle joint decoder was not given a graph")
        val graph = input.graph.get
        val sentence = input.sentence
        var feats = new FeatureVector(weights.labelset)

        // ConceptInvoke features
        for (span <- graph.spans) {
            val words = span.words.split(" ").toList
            val conceptList = conceptInvoker.invoke(input, span.start)
            val matching = conceptList.filter(x => x.words == words && x.graphFrag == span.amr.prettyString(detail = 0, pretty = false, vars = Set.empty[String]))
            for (concept <- matching) {
                feats += FastFeatureVector.fromBasicFeatureVector(stage1Features.localFeatures(input, concept, span.start, span.end), weights.labelset)
            }
        }

        // GraphDecoder features
        for { node1 <- graph.nodes
              (label, node2) <- node1.relations } {
            feats += stage2Features.localFeatures(node1, node2, label)
        }
        feats += stage2Features.rootFeatures(graph.root)

        return DecoderResult(graph, feats, weights.dot(feats))
    }
}

