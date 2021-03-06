package de.hpi.rdse.jujo.actors.common.training;

import akka.actor.ActorRef;
import akka.actor.Props;
import de.hpi.rdse.jujo.actors.common.AbstractReapedActor;
import de.hpi.rdse.jujo.actors.common.WordEndpoint;
import de.hpi.rdse.jujo.training.EncodedSkipGram;
import de.hpi.rdse.jujo.training.Word2VecModel;
import de.hpi.rdse.jujo.wordManagement.Vocabulary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.math3.linear.RealVector;

import java.io.Serializable;

public class SkipGramReceiver extends AbstractReapedActor {

    public static Props props() {
        return Props.create(SkipGramReceiver.class, SkipGramReceiver::new);
    }

    @NoArgsConstructor @AllArgsConstructor @Builder @Getter
    public static class ProcessEncodedSkipGram implements Serializable {
        private static final long serialVersionUID = -6574596641614399323L;
        private EncodedSkipGram skipGram;
        private ActorRef wordEndpointResponsibleForInput;
        private int epoch;
    }

    private SkipGramReceiver() { }

    @Override
    public Receive createReceive() {
        return this.defaultReceiveBuilder()
                   .match(ProcessEncodedSkipGram.class, this::handle)
                   .matchAny(this::handleAny)
                   .build();
    }

    private void handle(ProcessEncodedSkipGram message) throws InterruptedException {
        if (!Vocabulary.getInstance().isComplete()) {
            this.log().info("Postponing encoded skip-gram because vocabulary is not completed yet");
            Thread.sleep(1000);
            this.self().tell(message, this.sender());
            return;
        }
        if (!Vocabulary.getInstance().containsLocally(message.getSkipGram().getExpectedOutput())) {
            this.log().debug(String.format("Discarding sub-sampled encoded skip-gram (expected output = \"%s\"",
                    message.getSkipGram().getExpectedOutput()));
            return;
        }

        this.log().debug(String.format("About to train on expected output %s", message.getSkipGram().getExpectedOutput()));

        RealVector inputGradient = Word2VecModel.getInstance().train(message.getSkipGram(), message.getEpoch());
        long oneHotIndex = message.getSkipGram().getEncodedInput().getOneHotIndex();
        message.getWordEndpointResponsibleForInput().tell(WordEndpoint.UpdateWeight.builder()
                                                                                   .gradient(inputGradient)
                                                                                   .oneHotIndex(oneHotIndex)
                                                                                   .epoch(message.getEpoch())
                                                                                   .build(), this.self());

        this.log().debug(String.format("Done training on expected output %s", message.getSkipGram().getExpectedOutput()));
    }
}
