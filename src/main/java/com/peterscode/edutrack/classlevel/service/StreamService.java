package com.peterscode.edutrack.classlevel.service;

import com.peterscode.edutrack.classlevel.dto.*;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StreamService {
    StreamResponseDto createStream(CreateStreamRequestDto requestDto, UserPrincipal principal);
    StreamResponseDto getStreamById(Long id, UserPrincipal principal);
    PageResponse<StreamResponseDto> getStreams(Long schoolId, Long classLevelId, Pageable pageable, UserPrincipal principal);
    List<StreamResponseDto> getStreamsByClassLevel(Long classLevelId, UserPrincipal principal);
    StreamResponseDto updateStream(Long id, UpdateStreamRequestDto requestDto, UserPrincipal principal);
    void deleteStream(Long id, UserPrincipal principal);
}
