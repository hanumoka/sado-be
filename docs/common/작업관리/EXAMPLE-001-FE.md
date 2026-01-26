# TASK-EXAMPLE-001-FE: Study 목록 검색 기능 (예시)

## 📋 작업 개요

- **작업 ID**: TASK-EXAMPLE-001-FE
- **연관 작업**: TASK-EXAMPLE-001-BE
- **우선순위**: High
- **예상 시간**: 2시간
- **작성일**: 2026-01-02
- **담당 터미널**: FE

---

## 🎯 요구사항

Study 목록 화면에 검색 기능을 추가합니다.

**기능**:
- 환자 이름 또는 Study ID로 검색
- 검색어 입력 시 실시간 필터링
- Debounce 처리 (300ms)
- 검색 결과가 없을 때 안내 메시지 표시
- 검색어 클리어 버튼

---

## 🔗 의존성

### BE 의존성
- [x] TASK-EXAMPLE-001-BE 완료 필요
- 필요한 API: `GET /api/studies/search?searchKeyword={keyword}&page={page}&size={size}`

### 선행 작업
- 없음

---

## 📁 수정 파일 목록

### 신규 생성
- `sado_fe/src/components/study/SearchBar.tsx`
- `sado_fe/src/hooks/useStudySearch.ts`

### 수정 필요
- `sado_fe/src/pages/StudyListPage.tsx`
- `sado_fe/src/services/studyService.ts`
- `sado_fe/src/types/study.ts`

---

## 🛠️ 구현 가이드

### 1단계: 타입 정의

**sado_fe/src/types/study.ts에 추가**:
```typescript
export interface StudySearchParams {
  searchKeyword: string;
  page?: number;
  size?: number;
}

export interface StudySearchResponse {
  studies: Study[];
  totalCount: number;
  currentPage: number;
}
```

### 2단계: API 서비스

**sado_fe/src/services/studyService.ts에 추가**:
```typescript
export const studyService = {
  // 기존 메서드들...

  searchStudies: async (params: StudySearchParams): Promise<ApiResponse<StudySearchResponse>> => {
    const { searchKeyword, page = 0, size = 20 } = params;
    const response = await fetch(
      `/api/studies/search?searchKeyword=${encodeURIComponent(searchKeyword)}&page=${page}&size=${size}`
    );
    return response.json();
  },
};
```

### 3단계: 커스텀 훅

**sado_fe/src/hooks/useStudySearch.ts 생성**:
```typescript
import { useQuery } from '@tanstack/react-query';
import { useState, useEffect } from 'react';
import { studyService } from '@/services/studyService';

export const useStudySearch = (initialKeyword = '') => {
  const [searchKeyword, setSearchKeyword] = useState(initialKeyword);
  const [debouncedKeyword, setDebouncedKeyword] = useState(initialKeyword);

  // Debounce 처리
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedKeyword(searchKeyword);
    }, 300);

    return () => clearTimeout(timer);
  }, [searchKeyword]);

  // TanStack Query
  const { data, isLoading, error } = useQuery({
    queryKey: ['studies', 'search', debouncedKeyword],
    queryFn: () => studyService.searchStudies({ searchKeyword: debouncedKeyword }),
    enabled: debouncedKeyword.length >= 2, // 최소 2자 이상
  });

  return {
    searchKeyword,
    setSearchKeyword,
    studies: data?.data?.studies || [],
    totalCount: data?.data?.totalCount || 0,
    isLoading,
    error,
  };
};
```

### 4단계: SearchBar 컴포넌트

**sado_fe/src/components/study/SearchBar.tsx 생성**:
```typescript
import { Search, X } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';

interface SearchBarProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}

export function SearchBar({ value, onChange, placeholder = '환자 이름 또는 Study ID 검색' }: SearchBarProps) {
  return (
    <div className="relative w-full max-w-md">
      <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
      <Input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="pl-10 pr-10"
      />
      {value && (
        <Button
          variant="ghost"
          size="icon"
          className="absolute right-1 top-1/2 h-7 w-7 -translate-y-1/2"
          onClick={() => onChange('')}
        >
          <X className="h-4 w-4" />
        </Button>
      )}
    </div>
  );
}
```

### 5단계: StudyListPage 통합

**sado_fe/src/pages/StudyListPage.tsx 수정**:
```typescript
import { SearchBar } from '@/components/study/SearchBar';
import { useStudySearch } from '@/hooks/useStudySearch';

export function StudyListPage() {
  const { searchKeyword, setSearchKeyword, studies, totalCount, isLoading, error } = useStudySearch();

  return (
    <div className="container mx-auto p-4">
      <div className="mb-6">
        <h1 className="text-2xl font-bold mb-4">Study 목록</h1>
        <SearchBar value={searchKeyword} onChange={setSearchKeyword} />
      </div>

      {isLoading && <p>검색 중...</p>}

      {error && <p className="text-red-500">검색 오류: {error.message}</p>}

      {!isLoading && studies.length === 0 && searchKeyword && (
        <p className="text-muted-foreground">검색 결과가 없습니다.</p>
      )}

      {studies.length > 0 && (
        <div>
          <p className="text-sm text-muted-foreground mb-2">
            총 {totalCount}개의 검색 결과
          </p>
          {/* 기존 Study 목록 렌더링 */}
        </div>
      )}
    </div>
  );
}
```

---

## ✅ 체크리스트

### UI/UX
- [ ] SearchBar 컴포넌트 구현
- [ ] 검색 아이콘 표시
- [ ] 클리어 버튼 표시
- [ ] 로딩 상태 표시
- [ ] 검색 결과 없음 메시지
- [ ] 에러 메시지 표시

### 기능
- [ ] 검색어 입력 시 실시간 검색
- [ ] Debounce 처리 (300ms)
- [ ] 검색어 클리어 버튼
- [ ] 최소 2자 이상 입력 시 검색

### API 연동
- [ ] studyService.searchStudies 구현
- [ ] API 에러 처리
- [ ] 타입 정의 (StudySearchParams, StudySearchResponse)

### 테스트
- [ ] SearchBar 입력 동작 확인
- [ ] Debounce 동작 확인 (300ms)
- [ ] API 호출 확인 (Network 탭)
- [ ] 검색 결과 렌더링 확인
- [ ] 에러 상황 테스트

---

## 🧪 테스트 시나리오

### 1. 정상 검색
1. 검색어 "Kim" 입력
2. 300ms 대기 후 API 호출 확인
3. 검색 결과 목록 표시 확인

### 2. 결과 없음
1. 존재하지 않는 이름 "XYZ999" 입력
2. "검색 결과가 없습니다" 메시지 확인

### 3. 검색어 클리어
1. 검색어 입력
2. X 버튼 클릭
3. 검색어 삭제 및 전체 목록 표시 확인

### 4. Debounce 테스트
1. "Kim" 입력 중 ("K" → "Ki" → "Kim")
2. 각 문자 입력 시 API 호출 안 됨 확인
3. 300ms 후 1번만 호출 확인

### 5. 최소 길이 검증
1. "K" (1자) 입력
2. API 호출 안 됨 확인
3. "Ki" (2자) 입력 시 호출 확인

---

## 📝 추가 노트

### 스타일링
- Tailwind CSS 사용
- shadcn/ui Input, Button 컴포넌트 활용
- lucide-react 아이콘 (Search, X)

### 접근성
- Input에 aria-label 추가
- 클리어 버튼에 aria-label="검색어 지우기"

### 성능
- Debounce로 불필요한 API 호출 방지
- TanStack Query 캐싱 활용

---

## 🔄 상태 추적

- [ ] 작업 시작
- [ ] 타입 정의 완료
- [ ] API 서비스 구현 완료
- [ ] 커스텀 훅 구현 완료
- [ ] SearchBar 컴포넌트 완료
- [ ] StudyListPage 통합 완료
- [ ] 테스트 완료
- [ ] 완료 → archive 이동
