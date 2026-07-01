package com.autoerd.application.metadata.usecase;

import com.autoerd.dto.ErdResponse;

public interface GetProjectErdUseCase {

    ErdResponse getProjectErd(GetProjectErdQuery query);
}
