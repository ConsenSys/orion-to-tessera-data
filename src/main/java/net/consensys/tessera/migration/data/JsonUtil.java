package net.consensys.tessera.migration.data;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import java.io.OutputStream;
import java.util.Map;

public class JsonUtil {

    static void prettyPrint(JsonObject jsonObject, OutputStream outputStream) {
        JsonWriterFactory writerFactory = Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));
        try(JsonWriter jsonWriter = writerFactory.createWriter(outputStream)) {
            jsonWriter.writeObject(jsonObject);
        }
    }

}
