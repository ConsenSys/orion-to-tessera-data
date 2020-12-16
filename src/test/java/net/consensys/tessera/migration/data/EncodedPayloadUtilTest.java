package net.consensys.tessera.migration.data;

import com.quorum.tessera.enclave.EncodedPayload;
import com.quorum.tessera.enclave.PayloadEncoder;
import org.junit.Test;

import javax.json.Json;
import javax.json.JsonObject;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

public class EncodedPayloadUtilTest {

    @Test
    public void doStuff() {


        JsonObject jsonObject = Json.createObjectBuilder().add("sender","arhIcNa+MuYXZabmzJD5B33F3dZgqb0hEbM3FZsylSg=")
                .add("nonce","g3/Ibyh2+rtpvaFo7QESs8bD9H+O/BGE")
                .add("encryptedKeys",Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("encoded","OkLneen9aj3aQ2525B9UhbXVYo+Yfhq0Yg6suCSdnoaIv9MxKtanpZMEhQ/BuQ58"))
                        .add(Json.createObjectBuilder().add("encoded","+dOvM0+1NfdK1MkS+/zK8wmLGVLKGbUFcwD5j0u/7scAlPdvXl6oRY6yY1JzuDKu"))
                )
                .add("cipherText","gHVlYS7ktd8C89E428OX4nGUmsPnXsBSLYETblRYeuL5pfkI4ciKPRnSiCamOvLKII1jH1bhUk5LKGkwAvSyMzwAVLeVizBapc2ip/e3rr1ocuYyjRgbeJPh7Ie4An6jHIDOpEEWf/FxTdRE1ASPCUR5eY0S8lgqSeyKYtYvcbegBZgLeUza/EXrWhMAs6B3D8CjPO1D2hHlIQ7/F/PbxOE/vKPb88PAFRcgGoOsda6gjswl6azTJatY/zDFkpX9wFMzW7IdFFN8S7ZUvhbb3H7v6KXzZhdycaNfR9i105HwtAslwMYYGLFG55bKjzNO/T30gR+iSoUyqDLLLhUMhxKYEgIQK9oMNJcNRWU6Ktq/dQht5ylMnkFFzIoSOHK9tAfzBZiFHfk3WgYZ0ipwBb+CLnkLvY9lLIlGTU+r/Dsq8woULNFmrnZpitE+qI2A+rsC3vFt1peJODQRQaktklDYLrXL+RTLCnqr4WPBwoQ3rnpECWlOtpgK43IoZNv93J0brE4dHZfJ4M3GCHFTIIKBS6Sha/Tvc3alATtNIGvsMVDeKJvNRKFaxqHUvXL7seerFwnFLcXBasectyxz6sZzpAPWw3o0+0yqrP24DactaS5d2VximGYAH3g/IDnIBjQZR+SpYF7n8HfzRzNv2njnkYe7aSLnF78B0yjVMojznWHZe3wIMQNizQ2WxiF5CRo/K8a/x0yUtOY7Qlf7TwIlPDzTi+3AFEVSCth0Ao+yiB/0kmxFxsfDy+lLVkKluk69hhSPxhFOm99kq5K0v69+TypNrQyzi0vv3I7500fxOprE90sjo4XwqqsjWX6+p1BhdilGa0DK3zjr+jz6tYHAMZWSVqB/3+7G9PGvNaHxCK3gaDankzZlqD8Kg+SUoAQW8tgvvGUIiMm0tc46C8RZCUdhcKlp+RxMjoMZPjqXlaWwnMqOdrtRMmq1fQmvwMnB57L/p4lR1MQK7VMpECLctBNwejuiyeYVxP4CnBSnw22oEntCg6TRQgajLeteIyHlN0O/w2IfFPylJLRsF/FG76k6qnpBat0uhUlmhkXE0W0uYoaMXJ1MEQcttSE8IdGIeqzFN5gV05+7WaLrVQR4G/5pgfzDPNVx4n1Rrfw+0pdrSycoT6TT7jl1Pu9TGOH/6sBI7tJOEKNaIfWAdut4R9Qq5VrWXyaU1TXCNCVWnRYnzVE7WfPSx/Yc+1122KIV/q/spCNhMq63jOL1AawSM3xKO450woGDTp23Arfz8dxAPZRzgb4EXEdH8lyC/iymU2tby+kqq4x4EP9Zec7+EbczDdpTu7USlUDnrO8iGCwviKzt1C3yRjzTeVKjonmbZcdRb2gaMCL0EEAe75tfz1asr7UBF265gW5cNheGZq3V5xT4BQ8FynwpgSVVRu6YQCFdfL859aeUfJDebh1/u9b5UpHAmO2jqw==")
                .add("privacyGroupId","7ylbnPRF7xEwTs4t9GpMLl79nt/RGlxRrMmVVG5eD7c=")
                .build();

        JsonUtil.prettyPrint(jsonObject,System.out);

        EncodedPayload result = EncodedPayloadUtil.createFrom(jsonObject);

        assertThat(result).isNotNull();
        assertThat(result.getSenderKey()).isNotNull();
        assertThat(result.getSenderKey().encodeToBase64())
                .isEqualTo("arhIcNa+MuYXZabmzJD5B33F3dZgqb0hEbM3FZsylSg=");

        assertThat(result.getRecipientNonce()).isNotNull();
        assertThat(result.getRecipientNonce().getNonceBytes())
                .isEqualTo(Base64.getDecoder().decode("g3/Ibyh2+rtpvaFo7QESs8bD9H+O/BGE"));

        assertThat(result.getCipherText()).isEqualTo(Base64.getDecoder().decode(jsonObject.getString("cipherText")));

        assertThat(result.getRecipientKeys()).hasSize(2);

        PayloadEncoder payloadEncoder = PayloadEncoder.create();
        payloadEncoder.encode(result);

    }

}
