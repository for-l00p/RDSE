package de.hpi.rdse.jujo.startup;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import lombok.Getter;

@Parameters(commandDescription = "start a master actor system")
@Getter
public class MasterCommand extends CommandBase {

    public static final int DEFAULT_PORT = 7789;
    public static final int DEFAULT_DIMENSIONS = 100;
    public static final int DEFAULT_WINDOW_SIZE = 3;
    public static final int DEFAULT_NUMBER_OF_EPOCHS = 10;
    public static final float DEFAULT_LEARNING_RATE = 0.05f;
    public static final int DEFAULT_NUMBER_OF_NEGATIVE_SAMPLES = 5;

    @Parameter(names = {"-h", "--host"}, description = "host address of this system")
    String host = getDefaultHost();

    @Parameter(names = {"-i", "--input"}, description = "text corpus to train on", validateValueWith = FileValidator.class)
    String pathToInputFile;

    @Parameter(names = {"-t", "--temporary"}, description = "temporary working directory", validateValueWith = DirectoryValidator.class)
    String temporaryWorkingDirectory;

    @Parameter(names = {"--slaves"}, description = "number of slaves to wait for")
    int numberOfSlaves;

    @Parameter(names = {"-d", "--dimensions"}, description = "dimensionality of resulting word embeddings")
    int dimensions = DEFAULT_DIMENSIONS;

    @Parameter(names = {"-w", "--window-size"}, description = "size of window for building skip-grams")
    int windowSize = DEFAULT_WINDOW_SIZE;

    @Parameter(names = {"-e", "--epochs"}, description = "number of epochs to train")
    int numberOfEpochs = DEFAULT_NUMBER_OF_EPOCHS;

    @Parameter(names = {"-l", "--learning-rate"}, description = "initial learning rate")
    float learningRate = DEFAULT_LEARNING_RATE;

    @Parameter(names = {"-n", "--negative-samples"}, description = "number of negative samples")
    int numberOfNegativeSamples = DEFAULT_NUMBER_OF_NEGATIVE_SAMPLES;
}
