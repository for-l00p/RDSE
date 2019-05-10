package de.hpi.rdse.jujo.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class FilePartition {
    private final long readOffset;
    private final long readLength;

    public static FilePartition empty() {
        return new FilePartition(0, 0);
    }
}
