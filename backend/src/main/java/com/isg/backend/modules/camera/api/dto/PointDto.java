package com.isg.backend.modules.camera.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointDto {

    @NotNull(message = "x koordinatı boş olamaz")
    @Min(value = 0, message = "x koordinatı 0'dan küçük olamaz")
    @Max(value = 1, message = "x koordinatı 1'den büyük olamaz")
    private Double x;

    @NotNull(message = "y koordinatı boş olamaz")
    @Min(value = 0, message = "y koordinatı 0'dan küçük olamaz")
    @Max(value = 1, message = "y koordinatı 1'den büyük olamaz")
    private Double y;
}