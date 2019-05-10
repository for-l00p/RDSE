package de.hpi.rdse.jujo.utils.startup;

import com.beust.jcommander.IValueValidator;
import com.beust.jcommander.ParameterException;

import java.io.File;

public class FileValidator implements IValueValidator<String> {

    @Override
    public void validate(String name, String value) throws ParameterException {
        File file = new File(value);
        if (!file.exists()) {
            throw new ParameterException("the given " + name + " does not exist");
        }

        if (!file.isFile()) {
            throw new ParameterException("the given " + name + " is not a file");
        }
    }
}
