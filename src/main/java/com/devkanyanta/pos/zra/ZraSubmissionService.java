package com.devkanyanta.pos.zra;

import java.util.List;

import com.devkanyanta.pos.zra.dto.ZraSubmissionResponse;

public interface ZraSubmissionService {

    List<ZraSubmissionResponse> getAllSubmissions();

    ZraSubmissionResponse getSubmissionById(Long id);
}
