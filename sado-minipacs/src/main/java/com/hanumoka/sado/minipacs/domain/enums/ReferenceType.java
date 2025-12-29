package com.hanumoka.sado.minipacs.domain.enums;

/**
 * 파일 자산 참조 타입
 *
 * <p>FileAsset이 어떤 엔티티와 연결되어 있는지를 나타냅니다.
 *
 * <p>FileAsset은 polymorphic association을 사용하여 다양한 엔티티와 연결됩니다:
 * <pre>
 * FileAsset {
 *     referenceType: ReferenceType  // 참조 대상 엔티티 타입
 *     referenceId: Long             // 참조 대상 엔티티 PK
 * }
 * </pre>
 *
 * <p>사용 예시:
 * <pre>
 * {@code
 * // Instance의 썸네일 이미지
 * FileAsset thumbnail = FileAsset.builder()
 *     .category(FileCategory.SYSTEM)
 *     .referenceType(ReferenceType.INSTANCE)
 *     .referenceId(instance.getId())
 *     .storagePath("/thumbnails/instance_123.jpg")
 *     .build();
 *
 * // AI 분석 결과
 * FileAsset aiResult = FileAsset.builder()
 *     .category(FileCategory.AI_RESULT)
 *     .referenceType(ReferenceType.AI_ANALYSIS)
 *     .referenceId(aiAnalysis.getId())
 *     .storagePath("/ai_results/analysis_456.png")
 *     .build();
 *
 * // Study 전체 내보내기 ZIP
 * FileAsset exportZip = FileAsset.builder()
 *     .category(FileCategory.EXPORT)
 *     .referenceType(ReferenceType.STUDY)
 *     .referenceId(study.getId())
 *     .storagePath("/exports/study_789.zip")
 *     .expiresAt(LocalDateTime.now().plusDays(7))
 *     .build();
 * }
 * </pre>
 */
public enum ReferenceType {

    /**
     * DICOM Instance 참조
     *
     * <p>DICOM Instance(SOP Instance)와 연결된 파일입니다.
     *
     * <p>주요 사용 사례:
     * <ul>
     *   <li>썸네일 이미지 (JPEG) - DICOM 프레임 미리보기</li>
     *   <li>비디오 변환 결과 (MP4) - 멀티프레임 DICOM → MP4</li>
     *   <li>압축된 DICOM 파일 (ZIP) - 백업 목적</li>
     * </ul>
     *
     * <p>예시:
     * <pre>
     * {@code
     * Instance instance = instanceRepository.findById(123L);
     *
     * // 썸네일 생성
     * FileAsset thumbnail = FileAsset.builder()
     *     .category(FileCategory.SYSTEM)
     *     .referenceType(ReferenceType.INSTANCE)
     *     .referenceId(instance.getId())
     *     .fileName("thumbnail.jpg")
     *     .fileSize(45678L)
     *     .expiresAt(LocalDateTime.now().plusDays(90))
     *     .build();
     * }
     * </pre>
     *
     * <p>Instance 삭제 시:
     * <ul>
     *   <li>연관된 FileAsset도 함께 삭제 (CASCADE)</li>
     *   <li>물리 파일도 함께 삭제</li>
     * </ul>
     */
    INSTANCE,

    /**
     * DICOM Series 참조
     *
     * <p>DICOM Series와 연결된 파일입니다.
     *
     * <p>주요 사용 사례:
     * <ul>
     *   <li>Series 전체 ZIP - 모든 Instance를 하나의 압축 파일로</li>
     *   <li>Series 썸네일 - 대표 이미지</li>
     *   <li>Series 리포트 - Series별 분석 요약</li>
     * </ul>
     *
     * <p>예시:
     * <pre>
     * {@code
     * Series series = seriesRepository.findById(456L);
     *
     * // Series 전체 내보내기
     * FileAsset exportZip = FileAsset.builder()
     *     .category(FileCategory.EXPORT)
     *     .referenceType(ReferenceType.SERIES)
     *     .referenceId(series.getId())
     *     .fileName("series_export.zip")
     *     .fileSize(1234567890L)
     *     .expiresAt(LocalDateTime.now().plusDays(7))
     *     .build();
     * }
     * </pre>
     */
    SERIES,

    /**
     * DICOM Study 참조
     *
     * <p>DICOM Study와 연결된 파일입니다.
     *
     * <p>주요 사용 사례:
     * <ul>
     *   <li>Study 전체 ZIP - 모든 Series를 하나의 압축 파일로</li>
     *   <li>Study 리포트 (PDF) - 검사 전체 소견서</li>
     *   <li>임상 문서 - Study와 관련된 진단서</li>
     * </ul>
     *
     * <p>예시:
     * <pre>
     * {@code
     * Study study = studyRepository.findById(789L);
     *
     * // Study 소견서 PDF
     * FileAsset reportPdf = FileAsset.builder()
     *     .category(FileCategory.CLINICAL_DOC)
     *     .referenceType(ReferenceType.STUDY)
     *     .referenceId(study.getId())
     *     .fileName("diagnostic_report.pdf")
     *     .fileSize(987654L)
     *     // 임상 문서는 영구 보관 (expiresAt = null)
     *     .build();
     * }
     * </pre>
     */
    STUDY,

    /**
     * Patient 참조
     *
     * <p>환자와 연결된 파일입니다.
     *
     * <p>주요 사용 사례:
     * <ul>
     *   <li>환자 동의서 스캔본 (PDF)</li>
     *   <li>환자 전체 기록 내보내기 (ZIP)</li>
     *   <li>환자별 통합 리포트 (PDF)</li>
     * </ul>
     *
     * <p>예시:
     * <pre>
     * {@code
     * Patient patient = patientRepository.findById(101L);
     *
     * // 환자 동의서
     * FileAsset consentForm = FileAsset.builder()
     *     .category(FileCategory.CLINICAL_DOC)
     *     .referenceType(ReferenceType.PATIENT)
     *     .referenceId(patient.getId())
     *     .fileName("consent_form.pdf")
     *     .fileSize(234567L)
     *     // 법적 문서는 영구 보관 (expiresAt = null)
     *     .build();
     * }
     * </pre>
     *
     * <p>주의사항:
     * <ul>
     *   <li>Patient 삭제는 의료법상 제한 → FileAsset도 함께 유지</li>
     *   <li>익명화 시 FileAsset은 유지하되 referenceId만 익명 Patient로 변경</li>
     * </ul>
     */
    PATIENT,

    /**
     * AI 분석 참조
     *
     * <p>AI 분석 작업(AiAnalysis 엔티티)과 연결된 파일입니다.
     *
     * <p>주요 사용 사례:
     * <ul>
     *   <li>Segmentation 이미지 (PNG) - 프레임별 심장 영역 시각화</li>
     *   <li>분석 리포트 (PDF) - EF, EDV, ESV 지표 및 차트</li>
     *   <li>분석 메타데이터 (JSON) - 분석 파라미터 및 원시 데이터</li>
     *   <li>비디오 시각화 (MP4) - Segmentation 결과 애니메이션</li>
     * </ul>
     *
     * <p>예시:
     * <pre>
     * {@code
     * AiAnalysis analysis = aiAnalysisRepository.findById(202L);
     *
     * // EchoNet-Dynamic Segmentation 이미지
     * for (int frameIndex = 0; frameIndex < analysis.getTotalFrames(); frameIndex++) {
     *     FileAsset segImage = FileAsset.builder()
     *         .category(FileCategory.AI_RESULT)
     *         .referenceType(ReferenceType.AI_ANALYSIS)
     *         .referenceId(analysis.getId())
     *         .fileName(String.format("segmentation_frame_%03d.png", frameIndex))
     *         .storagePath(String.format("/ai_results/%d/seg/frame_%03d.png",
     *                                     analysis.getId(), frameIndex))
     *         .fileSize(128000L)
     *         // AI 결과는 영구 보관 (의료 기록의 일부)
     *         .build();
     * }
     *
     * // 분석 리포트 PDF
     * FileAsset reportPdf = FileAsset.builder()
     *     .category(FileCategory.AI_RESULT)
     *     .referenceType(ReferenceType.AI_ANALYSIS)
     *     .referenceId(analysis.getId())
     *     .fileName("ef_analysis_report.pdf")
     *     .storagePath(String.format("/ai_results/%d/report.pdf", analysis.getId()))
     *     .fileSize(567890L)
     *     .build();
     * }
     * </pre>
     *
     * <p>보관 정책:
     * <ul>
     *   <li>TTL: 없음 (영구 보관)</li>
     *   <li>이유: AI 분석 결과는 진단 근거로 사용되는 의료 기록</li>
     *   <li>삭제 조건: 원본 DICOM이 삭제되고 + 법적 보관 기간 경과 시만</li>
     * </ul>
     *
     * <p>Storage Tier 전략:
     * <ul>
     *   <li>최근 30일: HOT (자주 접근)</li>
     *   <li>30일~1년: WARM (가끔 접근)</li>
     *   <li>1년 이상: COLD (거의 접근 안 함, 아카이브 대상)</li>
     * </ul>
     */
    AI_ANALYSIS
}
