package com.hanumoka.sado.minipacs.domain.enums;

/**
 * 파일 자산 카테고리
 *
 * <p>FileAsset의 파일 종류를 분류하며, 각 카테고리는 보관 정책과 접근 권한을 결정합니다.
 *
 * <p>사용 예시:
 * <pre>
 * {@code
 * FileAsset aiResult = FileAsset.builder()
 *     .category(FileCategory.AI_RESULT)
 *     .build();
 *
 * // AI 분석 결과는 영구 보관
 * if (file.getCategory() == FileCategory.AI_RESULT) {
 *     // 의료 기록으로 영구 저장
 * }
 * }
 * </pre>
 */
public enum FileCategory {

    /**
     * AI 분석 결과
     *
     * <p>AI 모델(EchoNet-Dynamic 등)이 생성한 분석 결과 파일입니다.
     *
     * <p>포함 파일 종류:
     * <ul>
     *   <li>Segmentation 이미지 (PNG) - 프레임별 심장 영역 시각화</li>
     *   <li>분석 리포트 (PDF) - EF, EDV, ESV 등 지표</li>
     *   <li>분석 메타데이터 (JSON) - 분석 파라미터 및 결과</li>
     * </ul>
     *
     * <p>보관 정책:
     * <ul>
     *   <li>TTL: 없음 (영구 보관)</li>
     *   <li>이유: 의료 기록의 일부 (진단 근거)</li>
     *   <li>Storage Tier: HOT (자주 접근)</li>
     * </ul>
     *
     * <p>참조 타입: AI_ANALYSIS
     */
    AI_RESULT,

    /**
     * 임상 문서
     *
     * <p>진단서, 소견서 등 임상 관련 문서 파일입니다.
     *
     * <p>포함 파일 종류:
     * <ul>
     *   <li>진단 리포트 (PDF)</li>
     *   <li>소견서 (PDF/DOCX)</li>
     *   <li>환자 동의서 스캔본 (PDF)</li>
     * </ul>
     *
     * <p>보관 정책:
     * <ul>
     *   <li>TTL: 없음 (영구 보관)</li>
     *   <li>이유: 의료법 의무 보관 (10년 이상)</li>
     *   <li>Storage Tier: WARM (가끔 접근)</li>
     * </ul>
     *
     * <p>참조 타입: PATIENT, STUDY
     */
    CLINICAL_DOC,

    /**
     * 시스템 파일
     *
     * <p>시스템이 생성하는 내부 파일입니다.
     *
     * <p>포함 파일 종류:
     * <ul>
     *   <li>썸네일 이미지 (JPEG) - DICOM 미리보기</li>
     *   <li>비디오 변환 결과 (MP4) - DICOM → MP4 트랜스코딩</li>
     *   <li>임시 파일 (TMP) - 중간 처리 결과</li>
     * </ul>
     *
     * <p>보관 정책:
     * <ul>
     *   <li>TTL: 90일 (자동 삭제)</li>
     *   <li>이유: 재생성 가능한 파생 데이터</li>
     *   <li>Storage Tier: COLD (거의 접근 안 함)</li>
     * </ul>
     *
     * <p>참조 타입: INSTANCE
     */
    SYSTEM,

    /**
     * 내보내기 파일
     *
     * <p>사용자가 요청한 데이터 내보내기 결과입니다.
     *
     * <p>포함 파일 종류:
     * <ul>
     *   <li>Study 전체 ZIP</li>
     *   <li>CSV 내보내기</li>
     *   <li>PDF 리포트 번들</li>
     * </ul>
     *
     * <p>보관 정책:
     * <ul>
     *   <li>TTL: 7일 (자동 삭제)</li>
     *   <li>이유: 일회성 다운로드 목적</li>
     *   <li>Storage Tier: HOT (다운로드 대기)</li>
     * </ul>
     *
     * <p>참조 타입: STUDY, SERIES
     */
    EXPORT
}
