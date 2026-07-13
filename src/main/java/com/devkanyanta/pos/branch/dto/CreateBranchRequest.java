package com.devkanyanta.pos.branch.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateBranchRequest {

    private String name;

    private String zraTpin;

    private String phone;

    private String email;

    private String address;
}
