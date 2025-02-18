package com.example.documentloader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OCIDocumentLoader {
    private final ObjectStorage objectStorage;
    private final String namespace;

    public OCIDocumentLoader(ObjectStorage objectStorage, String namespace) {
        this.objectStorage = objectStorage;
        this.namespace = namespace;
    }

    public Stream<String> streamDocuments(String bucket, String prefix) {
        return listObjects(bucket, prefix).stream()
                .map(o -> {
                    try {
                        return getObjectText(bucket, o);
                    } catch (Exception e) {
                        log.error("Failed to retrieve object {} text from bucket {}", bucket, o, e);
                        return null;
                    }
                }).filter(Objects::nonNull);
    }

    private String getObjectText(String bucket, String key) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucket)
                .objectName(key)
                .build();
        GetObjectResponse response = objectStorage.getObject(request);
        InputStream in = response.getInputStream();
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int read;
        while ((read = in.read(data, 0, data.length)) != -1) {
            buff.write(data, 0, read);
        }
        buff.flush();
        return buff.toString(StandardCharsets.UTF_8);
    }

    private List<String> listObjects(String bucket, String prefix) {
        ListObjectsRequest.Builder requestBuilder = ListObjectsRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucket)
                .prefix(prefix);
        ListObjectsResponse response = objectStorage.listObjects(requestBuilder.build());
        List<String> objectNames = new ArrayList<>(toObjectNames(response));
        while (response.getListObjects().getNextStartWith() != null) {
            requestBuilder = ListObjectsRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucket)
                    .prefix(prefix)
                    .start(response.getListObjects().getNextStartWith());
            response = objectStorage.listObjects(requestBuilder.build());
            objectNames.addAll(toObjectNames(response));
        }
        return objectNames;
    }

    private List<String> toObjectNames(ListObjectsResponse response) {
        return response.getListObjects().getObjects()
                .stream()
                .map(ObjectSummary::getName)
                .filter(name -> !name.endsWith("/"))
                // ignore empty objects and directories
                .collect(Collectors.toList());
    }
}
