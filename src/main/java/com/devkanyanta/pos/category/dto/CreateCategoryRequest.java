package com.devkanyanta.pos.category.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCategoryRequest {

    private String name;

    private String description;
}