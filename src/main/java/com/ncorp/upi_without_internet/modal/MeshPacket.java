package com.ncorp.upi_without_internet.modal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.NoArgsConstructor;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeshPacket {
    @NotNull
    private String packetId;

    @Min(0)
    private int ttl;

    @NotNull
    private Long createdAt;


    @NotBlank
    private String cipherText;

}
