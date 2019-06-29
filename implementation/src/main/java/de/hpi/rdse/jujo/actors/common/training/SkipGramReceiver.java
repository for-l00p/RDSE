package de.hpi.rdse.jujo.actors.common.training;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.RootActorPath;
import de.hpi.rdse.jujo.actors.common.AbstractReapedActor;
import de.hpi.rdse.jujo.actors.common.WordEndpoint;
import de.hpi.rdse.jujo.training.EncodedSkipGram;
import de.hpi.rdse.jujo.training.UnencodedSkipGram;
import de.hpi.rdse.jujo.training.Word2VecModel;
import de.hpi.rdse.jujo.wordManagement.Vocabulary;
import de.hpi.rdse.jujo.wordManagement.WordEndpointResolver;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.math3.linear.RealVector;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SkipGramReceiver extends AbstractReapedActor {

    public static Props props() {
        return Props.create(SkipGramReceiver.class, SkipGramReceiver::new);
    }

    @NoArgsConstructor @AllArgsConstructor @Getter
    public static class ProcessUnencodedSkipGrams implements Serializable {
        private static final long serialVersionUID = 735332284132943544L;
        private List<UnencodedSkipGram> skipGrams = new ArrayList<>();
    }

    @NoArgsConstructor @AllArgsConstructor @Getter
    public static class ProcessEncodedSkipGram implements Serializable {
        private static final long serialVersionUID = -6574596641614399323L;
        private EncodedSkipGram skipGram;
    }

    @NoArgsConstructor
    public static class SkipGramChunkTransferred implements Serializable {
        private static final long serialVersionUID = -3803848151388038254L;
    }

    private final Map<RootActorPath, Map<String, List<String>>> unencodedSkipGramsByActor = new HashMap<>();

    private SkipGramReceiver() {
    }

    @Override
    public Receive createReceive() {
        return this.defaultReceiveBuilder()
                   .match(ProcessEncodedSkipGram.class, this::handle)
                   .match(ProcessUnencodedSkipGrams.class, this::handle)
                   .match(SkipGramChunkTransferred.class, this::handle)
                   .match(SkipGramDistributor.RequestNextSkipGramChunk.class, this::handle)
                   .matchAny(this::handleAny)
                   .build();
    }

    private void handle(ProcessEncodedSkipGram message) {
        if (!Vocabulary.getInstance().isComplete()) {
            this.log().info("Postponing encoded skip-gram because vocabulary is not completed yet");
            this.self().tell(message, this.sender());
            return;
        }
        if (!Vocabulary.getInstance().containsLocally(message.getSkipGram().getExpectedOutput())) {
            return;
        }
        RealVector inputGradient = Word2VecModel.getInstance().train(message.getSkipGram());
        long oneHotIndex = message.getSkipGram().getEncodedInput().getOneHotIndex();
        this.sender().tell(new WordEndpoint.UpdateWeight(oneHotIndex, inputGradient), this.self());
    }

    private void handle(ProcessUnencodedSkipGrams message) {
        if (!Vocabulary.getInstance().isComplete()) {
            this.log().info(String.format("Postponing %d unencoded skip-grams because vocabulary is not completed " +
                    "yet", message.getSkipGrams().size()));
            this.self().tell(message, this.sender());
            return;
        }
        this.log().debug(String.format("Processing %d unencoded skip-grams", message.getSkipGrams().size()));
        for (UnencodedSkipGram unencodedSkipGram : message.getSkipGrams()) {
            if (!Vocabulary.getInstance().containsLocally(unencodedSkipGram.getExpectedOutput())) {
                continue;
            }
            for (EncodedSkipGram encodedSkipGram : unencodedSkipGram.extractEncodedSkipGrams()) {
                this.self().tell(new ProcessEncodedSkipGram(encodedSkipGram), WordEndpointResolver.getInstance().localWordEndpoint());
            }
            this.addToUnencodedSkipGramsToResolve(unencodedSkipGram);
        }
        this.resolveUnencodedSkipGrams();
    }

    private void addToUnencodedSkipGramsToResolve(UnencodedSkipGram skipGram) {
        for (String inputWord : skipGram.getInputs()) {
            RootActorPath receiver = WordEndpointResolver.getInstance().resolve(inputWord).path().root();
            this.unencodedSkipGramsByActor.putIfAbsent(receiver, new HashMap<>());
            this.unencodedSkipGramsByActor.get(receiver).putIfAbsent(skipGram.getExpectedOutput(), new ArrayList<>());
            this.unencodedSkipGramsByActor.get(receiver).get(skipGram.getExpectedOutput()).add(inputWord);
        }
    }

    private void resolveUnencodedSkipGrams() {
        for (RootActorPath remote : this.unencodedSkipGramsByActor.keySet()) {
            WordEndpoint.EncodeSkipGrams message = new WordEndpoint.EncodeSkipGrams();
            for (String expectedOutput : this.unencodedSkipGramsByActor.get(remote).keySet()) {
                UnencodedSkipGram unencodedSkipGram = new UnencodedSkipGram(expectedOutput);
                unencodedSkipGram.getInputs().addAll(this.unencodedSkipGramsByActor.get(remote).get(expectedOutput));
                message.getUnencodedSkipGrams().add(unencodedSkipGram);
            }
            WordEndpointResolver.getInstance().wordEndpointOf(remote).tell(message, this.self());
        }
        this.unencodedSkipGramsByActor.clear();
    }

    private void handle(SkipGramChunkTransferred message) {
        this.self().tell(new SkipGramDistributor.RequestNextSkipGramChunk(), this.sender());
    }

    private void handle(SkipGramDistributor.RequestNextSkipGramChunk message) {
        this.sender().tell(message, WordEndpointResolver.getInstance().localWordEndpoint());
    }

}
