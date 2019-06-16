package de.hpi.rdse.jujo.actors.common;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import de.hpi.rdse.jujo.actors.common.training.SkipGramReceiver;
import de.hpi.rdse.jujo.training.EncodedSkipGram;
import de.hpi.rdse.jujo.training.UnencodedSkipGram;
import de.hpi.rdse.jujo.training.Word2VecModel;
import de.hpi.rdse.jujo.training.WordEmbedding;
import de.hpi.rdse.jujo.wordManagement.Vocabulary;
import de.hpi.rdse.jujo.wordManagement.WordEndpointResolver;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class WordEndpoint extends AbstractReapedActor {

    public static final String DEFAULT_NAME = "WordEndpoint";

    public static Props props() {
        return Props.create(WordEndpoint.class, WordEndpoint::new);
    }

    @Builder @NoArgsConstructor @AllArgsConstructor @Getter
    public static class WordEndpoints implements Serializable {
        private static final long serialVersionUID = 8070089151352318828L;
        private List<ActorRef> endpoints;
    }

    @NoArgsConstructor
    public static class VocabularyCreated implements Serializable {
        private static final long serialVersionUID = 5126582840330122184L;
    }

    @NoArgsConstructor
    public static class VocabularyCompleted implements Serializable {
        private static final long serialVersionUID = -8133305903846340892L;
    }

    @NoArgsConstructor @AllArgsConstructor @Getter
    public static class EncodeSkipGrams implements Serializable {
        private static final long serialVersionUID = 4648091561498065299L;
        private List<UnencodedSkipGram> unencodedSkipGrams = new ArrayList<>();
    }

    private final ActorRef subsampler;
    private final ActorRef vocabularyDistributor;
    private ActorRef vocabularyReceiver;

    private WordEndpoint() {
        WordEndpointResolver.createInstance(this.self());
        this.subsampler = this.context().actorOf(Subsampler.props());
        this.vocabularyDistributor = this.context().actorOf(VocabularyDistributor.props());
    }

    @Override
    public Receive createReceive() {
        return this.defaultReceiveBuilder()
                   .match(WordEndpoints.class, this::handle)
                   .match(Subsampler.WordsCounted.class, this::handle)
                   .match(VocabularyCreated.class, this::handle)
                   .match(Subsampler.TakeOwnershipForWordCounts.class, this::handle)
                   .match(Subsampler.ConfirmWordOwnershipDistribution.class, this::handle)
                   .match(VocabularyReceiver.ProcessVocabulary.class, this::handle)
                   .match(VocabularyCompleted.class, this::handle)
                   .match(SkipGramReceiver.ProcessUnencodedSkipGrams.class, this::handle)
                   .match(SkipGramReceiver.ProcessEncodedSkipGram.class, this::handle)
                   .match(EncodeSkipGrams.class, this::handle)
                   .matchAny(this::handleAny)
                   .build();
    }

    private void handle(WordEndpoints message) {
        if (WordEndpointResolver.getInstance().isReadyToResolve()) {
            this.log().warning("Received WordEndpoints message although already received earlier.");
        }

        this.log().info("Received all WordEndpoints");
        WordEndpointResolver.getInstance().all().addAll(message.getEndpoints());
        this.subsampler.tell(message, this.sender());
    }

    private void handle(Subsampler.WordsCounted message) {
        this.sender().tell(PoisonPill.getInstance(), ActorRef.noSender());
        this.subsampler.tell(message, this.sender());
    }

    private void handle(VocabularyCreated message) {
        this.sender().tell(PoisonPill.getInstance(), ActorRef.noSender());

        this.vocabularyDistributor.tell(new VocabularyDistributor.DistributeVocabulary(), this.self());
        this.context().parent().tell(message, this.self());
    }

    private void handle(Subsampler.TakeOwnershipForWordCounts message) {
        this.subsampler.tell(message, this.sender());
    }

    private void handle(Subsampler.ConfirmWordOwnershipDistribution message) {
        this.subsampler.tell(message, this.sender());
    }

    private void handle(VocabularyReceiver.ProcessVocabulary message) {
        if (this.vocabularyReceiver == null) {
            this.vocabularyReceiver = this.context().actorOf(VocabularyReceiver.props());
        }

        this.vocabularyReceiver.tell(message, this.sender());
    }

    private void handle(VocabularyCompleted message) {
        this.sender().tell(PoisonPill.getInstance(), ActorRef.noSender());
        this.context().parent().tell(new WorkerCoordinator.VocabularyReadyForTraining(), this.self());
    }

    private void handle(SkipGramReceiver.ProcessUnencodedSkipGrams message) {
        this.context().parent().tell(message, this.sender());
    }

    private void handle(SkipGramReceiver.ProcessEncodedSkipGram message) {
        this.context().parent().tell(message, this.sender());
    }

    private void handle(EncodeSkipGrams message) {
        for (UnencodedSkipGram unencodedSkipGram : message.getUnencodedSkipGrams()) {
            for (String input : unencodedSkipGram.getInputs()) {
                if (!Vocabulary.getInstance().containsLocally(input)) {
                    continue;
                }
                WordEmbedding embeddedInput = Word2VecModel.getInstance().createEmbedding(input);
                EncodedSkipGram encodedSkipGram = new EncodedSkipGram(unencodedSkipGram.getExpectedOutput(), embeddedInput);
                this.sender().tell(new SkipGramReceiver.ProcessEncodedSkipGram(encodedSkipGram), this.self());
            }
        }
    }
}
