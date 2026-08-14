package com.isg.backend.modules.camera.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RestrictedZoneUpdateReq {

    @NotBlank(message = "Yasaklı alan adı boş olamaz")
    private String name;

    @NotNull(message = "Poligon noktaları (polygon) eksik olamaz")
    @Size(min = 3, message = "Poligon kapalı bir alan oluşturabilmek için en az 3 noktadan oluşmalıdır")
    @Valid // Bu anotasyon, liste içindeki her bir PointDto'nun da kendi içindeki Min/Max kurallarından geçmesini sağlar
    private List<PointDto> polygon;
}