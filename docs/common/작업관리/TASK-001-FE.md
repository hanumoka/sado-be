# TASK-001-FE: DICOM Viewer 2x2 멀티 Viewport 리팩토링

**생성일**: 2026-01-02
**담당**: Frontend
**우선순위**: P1 (중요)
**예상 소요 시간**: 11시간
**상태**: 대기 중

---

## 요구사항

DICOM Viewer를 단일 viewport에서 2x2 그리드 멀티 viewport로 리팩토링합니다.

### 핵심 기능

1. **도구 제거**: 길이, 각도, ROI 도구 제거 (WindowLevel, Pan, Zoom만 유지)
2. **2x2 그리드**: 연속된 4개 DICOM을 2x2 격자로 동시 표시
3. **사이드바**: 오른쪽에 전체 DICOM 리스트 세로 표시 (썸네일 포함)
4. **전체 화면**: 더블클릭으로 viewport 확대/복귀
5. **Cine Player**:
   - 각 viewport마다 개별 재생/일시정지/중지
   - 4개 viewport 동기화 재생 (공통 컨트롤)

### 확정된 설계

- **Instance 분배**: 연속된 4개 슬라이스 (#1,#2,#3,#4 → #2,#3,#4,#5)
- **사이드바 클릭**: 클릭한 instance 중심으로 4개 표시
- **재생 방식**: 4개 viewport 동기화 재생 + 개별 재생
- **슬라이스 4개 미만**: 빈 화면은 검은 배경 "No Image"
- **사이드바 썸네일**: 실제 이미지 캡처 (또는 백엔드 API)

---

## 의존성

### Backend 의존성
- ❌ 없음 (Frontend 단독 작업)

### Frontend 의존성
- ✅ Cornerstone3D 4.15.1 (이미 설치됨)
- ✅ React 19.2.0
- ✅ TypeScript 5.9.3

---

## 파일 목록

### 수정할 파일 (5개)

1. **src/features/dicom-viewer/types/viewer.ts**
   - ViewerTool 타입 수정 (Length/Angle/Rectangle 제거)

2. **src/features/dicom-viewer/components/ViewerToolbar.tsx**
   - 도구 버튼 제거

3. **src/lib/cornerstone/initCornerstone.ts**
   - LengthTool 추가 제거

4. **src/features/dicom-viewer/components/DicomViewer.tsx** ⭐ 핵심
   - 단일 viewport → 4개 viewport
   - currentIndex → currentBaseIndex
   - Grid/Fullscreen 모드 전환

5. **src/app/pages/DicomViewerPage.tsx**
   - 레이아웃 변경 (Viewer + Sidebar)
   - 상태 관리 (currentBaseIndex 끌어올림)

### 신규 파일 (5개)

1. **src/features/dicom-viewer/components/InstanceSidebar.tsx**
   - Instance 목록 세로 스크롤
   - 현재 표시 중인 4개 하이라이트

2. **src/features/dicom-viewer/components/CinePlayerControls.tsx**
   - 동기화 재생 컨트롤 (재생/일시정지/정지, FPS)

3. **src/features/dicom-viewer/components/ViewportCineControl.tsx**
   - 개별 viewport 재생 버튼

4. **src/features/dicom-viewer/hooks/useCinePlayer.ts**
   - setInterval 기반 재생 로직

5. **src/features/dicom-viewer/hooks/useThumbnails.ts** (선택사항)
   - 썸네일 생성 Hook

---

## 구현 가이드

### Step 1: 도구 제거 (30분)

#### 1.1 viewer.ts 타입 수정

**파일**: `src/features/dicom-viewer/types/viewer.ts`

```typescript
// 수정 전
export type ViewerTool =
  | 'WindowLevel'
  | 'Zoom'
  | 'Pan'
  | 'Length'      // 삭제
  | 'Angle'       // 삭제
  | 'Rectangle'   // 삭제
  | 'Reset'

// 수정 후
export type ViewerTool =
  | 'WindowLevel'
  | 'Zoom'
  | 'Pan'
  | 'Reset'
```

#### 1.2 ViewerToolbar.tsx 버튼 제거

**파일**: `src/features/dicom-viewer/components/ViewerToolbar.tsx`

```typescript
// tools 배열에서 삭제
const tools = [
  { name: 'WindowLevel', icon: Sun, label: '창/레벨' },
  { name: 'Zoom', icon: Maximize2, label: '확대' },
  { name: 'Pan', icon: Move, label: '이동' },
  // { name: 'Length', icon: Ruler, label: '길이' },    // 삭제
  { name: 'Reset', icon: RotateCcw, label: '초기화' },
]
```

#### 1.3 DicomViewer.tsx 도구 제거

**파일**: `src/features/dicom-viewer/components/DicomViewer.tsx`

- 197줄: `toolsToAdd`에서 LengthTool 제거
- 280줄: `setToolPassive('Length')` 제거
- 424줄: `mapViewerToolToCornerstone`에서 `case 'Length'` 제거

---

### Step 2: 2x2 그리드 레이아웃 (1시간)

#### 2.1 상수 및 Ref 수정

**파일**: `src/features/dicom-viewer/components/DicomViewer.tsx`

```typescript
// 기존
const VIEWPORT_ID = 'dicomViewerViewport'
const viewerRef = useRef<HTMLDivElement>(null)

// 수정 후
const VIEWPORT_IDS = ['viewport-0', 'viewport-1', 'viewport-2', 'viewport-3']
const viewportRefs = useRef<(HTMLDivElement | null)[]>([null, null, null, null])
```

#### 2.2 Viewport 생성 로직 수정

```typescript
// setupViewer 함수 내부
const viewportInputs: cornerstone.Types.PublicViewportInput[] = VIEWPORT_IDS.map((id, index) => ({
  viewportId: id,
  type: cornerstone.Enums.ViewportType.STACK,
  element: viewportRefs.current[index]!,
  defaultOptions: {
    background: [0, 0, 0] as cornerstone.Types.Point3,
  },
}))

// 모든 viewport enable
viewportInputs.forEach(input => {
  renderingEngine!.enableElement(input)
})

// Tool Group: 4개 viewport 모두 추가
VIEWPORT_IDS.forEach(id => {
  toolGroup!.addViewport(id, RENDERING_ENGINE_ID)
})
```

#### 2.3 JSX 렌더링 수정

```typescript
// Grid Mode: 2x2
<div className="grid grid-cols-2 grid-rows-2 gap-1 h-full p-1">
  {VIEWPORT_IDS.map((id, index) => (
    <div
      key={id}
      className="relative bg-black rounded overflow-hidden"
      onDoubleClick={() => handleViewportDoubleClick(index)}
    >
      <div
        ref={el => viewportRefs.current[index] = el}
        className="w-full h-full"
      />
    </div>
  ))}
</div>
```

---

### Step 3: 연속된 4개 Instance 표시 (1.5시간)

#### 3.1 상태 추가

```typescript
const [currentBaseIndex, setCurrentBaseIndex] = useState(0)
```

#### 3.2 loadImageStack 함수 수정

```typescript
const loadImageStack = useCallback(async () => {
  if (!series || instances.length === 0 || !renderingEngineRef.current) return

  try {
    setIsLoading(true)

    // 전체 imageIds 생성
    const allImageIds = instances.map(instance => {
      const wadoUrl = getWadoUriUrl(
        series.studyInstanceUid,
        series.seriesInstanceUid,
        instance.sopInstanceUid
      )
      const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:10201'
      return `wadouri:${API_BASE}${wadoUrl}`
    })

    // 4개 viewport에 연속된 imageIds 할당
    for (let i = 0; i < VIEWPORT_IDS.length; i++) {
      const viewport = renderingEngineRef.current.getViewport(VIEWPORT_IDS[i])

      if (!isStackViewport(viewport)) continue

      const startIndex = currentBaseIndex + i

      if (startIndex < allImageIds.length) {
        await viewport.setStack(allImageIds, startIndex)
        viewport.render()
      }
    }

    setIsLoading(false)
    setLoadError(null)
  } catch (error) {
    console.error('[DicomViewer] Failed to load image stack:', error)
    setLoadError('DICOM 이미지 로드에 실패했습니다.')
    setIsLoading(false)
  }
}, [series, instances, currentBaseIndex])
```

#### 3.3 네비게이션 함수 수정

```typescript
const handlePrevious = useCallback(() => {
  setCurrentBaseIndex(prev => Math.max(0, prev - 1))
}, [])

const handleNext = useCallback(() => {
  setCurrentBaseIndex(prev => Math.min(instances.length - 1, prev + 1))
}, [instances.length])
```

---

### Step 4: 사이드바 Instance 리스트 (1시간)

#### 4.1 InstanceSidebar.tsx 생성

**신규 파일**: `src/features/dicom-viewer/components/InstanceSidebar.tsx`

```typescript
import { useCallback } from 'react'
import { Image as ImageIcon } from 'lucide-react'
import type { ViewerInstance } from '../types/viewer'

interface InstanceSidebarProps {
  instances: ViewerInstance[]
  currentBaseIndex: number
  onInstanceClick: (index: number) => void
  thumbnails?: Map<number, string>
}

export default function InstanceSidebar({
  instances,
  currentBaseIndex,
  onInstanceClick,
  thumbnails,
}: InstanceSidebarProps) {
  const visibleIndices = [
    currentBaseIndex,
    currentBaseIndex + 1,
    currentBaseIndex + 2,
    currentBaseIndex + 3,
  ]

  return (
    <div className="w-80 bg-gray-900 border-l border-gray-800 flex flex-col">
      {/* 헤더 */}
      <div className="p-4 border-b border-gray-800">
        <h3 className="text-white font-semibold">Instance List</h3>
        <p className="text-gray-400 text-sm">{instances.length} Images</p>
      </div>

      {/* Instance 리스트 */}
      <div className="flex-1 overflow-y-auto">
        {instances.map((instance, index) => {
          const isVisible = visibleIndices.includes(index)
          const viewportIndex = isVisible ? visibleIndices.indexOf(index) : -1

          return (
            <div
              key={instance.sopInstanceUid}
              onClick={() => onInstanceClick(index)}
              className={`
                p-3 border-b border-gray-800 cursor-pointer transition-colors
                ${isVisible ? 'bg-blue-900/30 border-l-4 border-l-blue-500' : 'hover:bg-gray-800'}
              `}
            >
              <div className="flex items-center gap-3">
                {/* 썸네일 영역 */}
                <div className="w-16 h-16 bg-gray-800 rounded flex items-center justify-center">
                  {thumbnails?.has(index) ? (
                    <img src={thumbnails.get(index)} alt={`#${index + 1}`} className="w-full h-full object-cover" />
                  ) : (
                    <ImageIcon className="h-8 w-8 text-gray-600" />
                  )}
                </div>

                {/* 정보 */}
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <p className="text-white font-medium">#{instance.instanceNumber || index + 1}</p>
                    {isVisible && (
                      <span className="text-xs bg-blue-600 text-white px-2 py-0.5 rounded">
                        VP {viewportIndex + 1}
                      </span>
                    )}
                  </div>
                  <p className="text-gray-400 text-xs font-mono truncate">{instance.sopInstanceUid}</p>
                </div>
              </div>
            </div>
          )
        })}
      </div>

      {/* 푸터 */}
      <div className="p-4 border-t border-gray-800 bg-gray-950">
        <div className="text-center text-white">
          <p className="text-2xl font-bold">
            {currentBaseIndex + 1} - {Math.min(currentBaseIndex + 4, instances.length)}
          </p>
          <p className="text-xs text-gray-400">/ {instances.length} Images</p>
        </div>
      </div>
    </div>
  )
}
```

#### 4.2 DicomViewerPage.tsx 레이아웃 변경

**파일**: `src/app/pages/DicomViewerPage.tsx`

```typescript
import InstanceSidebar from '@/features/dicom-viewer/components/InstanceSidebar'

export default function DicomViewerPage() {
  const [currentBaseIndex, setCurrentBaseIndex] = useState(0)

  const handleInstanceClick = useCallback((index: number) => {
    setCurrentBaseIndex(index)
  }, [])

  return (
    <div className="fixed inset-0 flex flex-col bg-black">
      <Header />
      <ViewerToolbar />

      {/* 메인 영역 */}
      <div className="flex-1 flex">
        {/* 뷰어 */}
        <div className="flex-1 relative">
          {data && (
            <DicomViewer
              instances={data.instances}
              series={data.series}
              activeTool={activeTool}
              windowLevelPreset={windowLevelPreset}
              currentBaseIndex={currentBaseIndex}
              onBaseIndexChange={setCurrentBaseIndex}
            />
          )}
        </div>

        {/* 사이드바 */}
        {data && (
          <InstanceSidebar
            instances={data.instances}
            currentBaseIndex={currentBaseIndex}
            onInstanceClick={handleInstanceClick}
          />
        )}
      </div>
    </div>
  )
}
```

---

### Step 5: 동기화 재생 기능 (2시간)

#### 5.1 useCinePlayer.ts Hook 생성

**신규 파일**: `src/features/dicom-viewer/hooks/useCinePlayer.ts`

```typescript
import { useState, useCallback, useRef, useEffect } from 'react'

export type CineState = 'stopped' | 'playing' | 'paused'
export type CineFPS = 5 | 10 | 15 | 30

interface UseCinePlayerProps {
  maxIndex: number
  currentIndex: number
  onIndexChange: (index: number) => void
}

export function useCinePlayer({ maxIndex, currentIndex, onIndexChange }: UseCinePlayerProps) {
  const [state, setState] = useState<CineState>('stopped')
  const [fps, setFps] = useState<CineFPS>(10)
  const intervalRef = useRef<number | null>(null)

  const play = useCallback(() => setState('playing'), [])
  const pause = useCallback(() => setState('paused'), [])
  const stop = useCallback(() => {
    setState('stopped')
    onIndexChange(0)
  }, [onIndexChange])

  useEffect(() => {
    if (state !== 'playing') {
      if (intervalRef.current !== null) {
        clearInterval(intervalRef.current)
        intervalRef.current = null
      }
      return
    }

    const interval = 1000 / fps
    intervalRef.current = window.setInterval(() => {
      onIndexChange(prevIndex => (prevIndex >= maxIndex ? 0 : prevIndex + 1))
    }, interval)

    return () => {
      if (intervalRef.current !== null) clearInterval(intervalRef.current)
    }
  }, [state, fps, maxIndex, onIndexChange])

  return { state, fps, play, pause, stop, setFps }
}
```

#### 5.2 CinePlayerControls.tsx 컴포넌트 생성

**신규 파일**: `src/features/dicom-viewer/components/CinePlayerControls.tsx`

```typescript
import { Play, Pause, Square } from 'lucide-react'
import type { CineState, CineFPS } from '../hooks/useCinePlayer'

interface CinePlayerControlsProps {
  state: CineState
  fps: CineFPS
  onPlay: () => void
  onPause: () => void
  onStop: () => void
  onFpsChange: (fps: CineFPS) => void
}

export default function CinePlayerControls({
  state,
  fps,
  onPlay,
  onPause,
  onStop,
  onFpsChange,
}: CinePlayerControlsProps) {
  const fpsOptions: CineFPS[] = [5, 10, 15, 30]

  return (
    <div className="bg-gray-900 text-white p-4 border-t border-gray-800">
      <div className="flex items-center justify-center gap-6">
        {/* 재생 컨트롤 */}
        <div className="flex items-center gap-2">
          {state !== 'playing' ? (
            <button
              onClick={onPlay}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-md"
            >
              <Play className="h-4 w-4" />
              <span>재생</span>
            </button>
          ) : (
            <button
              onClick={onPause}
              className="flex items-center gap-2 px-4 py-2 bg-yellow-600 hover:bg-yellow-700 rounded-md"
            >
              <Pause className="h-4 w-4" />
              <span>일시정지</span>
            </button>
          )}

          <button
            onClick={onStop}
            className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 rounded-md"
          >
            <Square className="h-4 w-4" />
            <span>정지</span>
          </button>
        </div>

        {/* FPS 선택 */}
        <div className="flex items-center gap-2">
          <span className="text-sm text-gray-400">FPS:</span>
          <div className="flex gap-1">
            {fpsOptions.map(option => (
              <button
                key={option}
                onClick={() => onFpsChange(option)}
                className={`px-3 py-1 rounded text-sm ${
                  fps === option ? 'bg-blue-600' : 'bg-gray-700 hover:bg-gray-600'
                }`}
              >
                {option}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
```

#### 5.3 DicomViewerPage.tsx에 통합

```typescript
import { useCinePlayer } from '@/features/dicom-viewer/hooks/useCinePlayer'
import CinePlayerControls from '@/features/dicom-viewer/components/CinePlayerControls'

export default function DicomViewerPage() {
  const cinePlayer = useCinePlayer({
    maxIndex: data ? data.instances.length - 1 : 0,
    currentIndex: currentBaseIndex,
    onIndexChange: setCurrentBaseIndex,
  })

  return (
    <div className="fixed inset-0 flex flex-col">
      {/* ... */}

      {/* 동기화 재생 컨트롤 */}
      {data && (
        <CinePlayerControls
          state={cinePlayer.state}
          fps={cinePlayer.fps}
          onPlay={cinePlayer.play}
          onPause={cinePlayer.pause}
          onStop={cinePlayer.stop}
          onFpsChange={cinePlayer.setFps}
        />
      )}
    </div>
  )
}
```

---

### Step 6: 개별 재생 기능 (2시간)

#### 6.1 ViewportCineControl.tsx 생성

**신규 파일**: `src/features/dicom-viewer/components/ViewportCineControl.tsx`

```typescript
import { Play, Pause, Square } from 'lucide-react'
import { useState, useCallback, useRef, useEffect } from 'react'

interface ViewportCineControlProps {
  viewportIndex: number
  currentIndex: number
  maxIndex: number
  onIndexChange: (index: number) => void
}

export default function ViewportCineControl({
  viewportIndex,
  currentIndex,
  maxIndex,
  onIndexChange,
}: ViewportCineControlProps) {
  const [isPlaying, setIsPlaying] = useState(false)
  const intervalRef = useRef<number | null>(null)

  const handlePlay = useCallback(() => setIsPlaying(true), [])
  const handlePause = useCallback(() => setIsPlaying(false), [])
  const handleStop = useCallback(() => {
    setIsPlaying(false)
    onIndexChange(0)
  }, [onIndexChange])

  useEffect(() => {
    if (!isPlaying) {
      if (intervalRef.current) clearInterval(intervalRef.current)
      return
    }

    intervalRef.current = window.setInterval(() => {
      onIndexChange(prev => (prev >= maxIndex ? 0 : prev + 1))
    }, 100)

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [isPlaying, maxIndex, onIndexChange])

  return (
    <div className="absolute bottom-2 right-2 flex gap-1 bg-black/70 rounded px-2 py-1">
      <button onClick={isPlaying ? handlePause : handlePlay} className="p-1 hover:bg-white/10 rounded">
        {isPlaying ? <Pause className="h-3 w-3 text-white" /> : <Play className="h-3 w-3 text-white" />}
      </button>
      <button onClick={handleStop} className="p-1 hover:bg-white/10 rounded">
        <Square className="h-3 w-3 text-white" />
      </button>
    </div>
  )
}
```

#### 6.2 DicomViewer.tsx에 통합

```typescript
import ViewportCineControl from './ViewportCineControl'

// 각 viewport의 개별 인덱스 관리
const [viewportIndices, setViewportIndices] = useState([0, 0, 0, 0])

const handleViewportIndexChange = useCallback((vpIndex: number, newIndex: number) => {
  setViewportIndices(prev => {
    const next = [...prev]
    next[vpIndex] = newIndex
    return next
  })

  if (renderingEngineRef.current) {
    const viewport = renderingEngineRef.current.getViewport(VIEWPORT_IDS[vpIndex])
    if (isStackViewport(viewport)) {
      viewport.setImageIdIndex(newIndex)
      viewport.render()
    }
  }
}, [])

// JSX
{VIEWPORT_IDS.map((id, index) => (
  <div key={id} className="relative">
    <div ref={el => viewportRefs.current[index] = el} />

    <ViewportCineControl
      viewportIndex={index}
      currentIndex={viewportIndices[index]}
      maxIndex={instances.length - 1}
      onIndexChange={(newIndex) => handleViewportIndexChange(index, newIndex)}
    />
  </div>
))}
```

---

## 체크리스트

### Step 1: 도구 제거
- [ ] viewer.ts에서 ViewerTool 타입 수정
- [ ] ViewerToolbar.tsx에서 도구 버튼 제거
- [ ] DicomViewer.tsx에서 Length 도구 제거
- [ ] 테스트: 도구 모음에 WindowLevel/Zoom/Pan/Reset만 표시

### Step 2: 2x2 그리드
- [ ] DicomViewer.tsx 상수 및 Ref 수정
- [ ] setupViewer에서 4개 viewport 생성
- [ ] JSX 2x2 그리드 렌더링
- [ ] 테스트: 검은 화면 4개 표시

### Step 3: 연속된 4개 Instance
- [ ] currentBaseIndex 상태 추가
- [ ] loadImageStack 수정 (4개 viewport에 imageIds 할당)
- [ ] 네비게이션 함수 수정
- [ ] 테스트: VP0=#1, VP1=#2, VP2=#3, VP3=#4

### Step 4: 사이드바
- [ ] InstanceSidebar.tsx 생성
- [ ] DicomViewerPage.tsx 레이아웃 변경
- [ ] handleInstanceClick 구현
- [ ] 테스트: 사이드바 #10 클릭 → 4개 viewport 변경

### Step 5: 동기화 재생
- [ ] useCinePlayer.ts Hook 생성
- [ ] CinePlayerControls.tsx 컴포넌트 생성
- [ ] DicomViewerPage.tsx에 통합
- [ ] 테스트: 재생 → 4개 viewport 동시 이동

### Step 6: 개별 재생
- [ ] ViewportCineControl.tsx 생성
- [ ] DicomViewer.tsx에 viewportIndices 추가
- [ ] handleViewportIndexChange 구현
- [ ] 테스트: 각 viewport 개별 재생

### Step 7: 전체 화면 (선택사항)
- [ ] 더블클릭 핸들러 추가
- [ ] viewMode 상태 관리
- [ ] CSS 기반 viewport 숨김/표시

### Step 8: 썸네일 (선택사항)
- [ ] useThumbnails.ts Hook 생성 (또는 백엔드 API 호출)
- [ ] InstanceSidebar에 썸네일 표시

---

## 테스트 시나리오

### 시나리오 1: 기본 네비게이션
1. Series 진입 → VP0=#1, VP1=#2, VP2=#3, VP3=#4
2. "다음" 버튼 5번 → VP0=#6, VP1=#7, VP2=#8, VP3=#9
3. 사이드바 #15 클릭 → VP0=#15, VP1=#16, VP2=#17, VP3=#18

### 시나리오 2: 동기화 재생
1. "재생" 버튼 → 4개 viewport 동시 재생 (10fps)
2. FPS 5fps로 변경 → 속도 느려짐
3. "일시정지" → 현재 위치 유지
4. "정지" → 첫 슬라이스로 복귀

### 시나리오 3: 개별 재생
1. VP0 재생 버튼 → VP0만 재생
2. VP2 재생 버튼 → VP0, VP2 동시 재생
3. VP0 정지 → VP2만 재생

---

## 참고 자료

- **상세 구현 가이드**: `C:\Users\amagr\.claude\plans\async-forging-moler.md`
- **Cornerstone3D 문서**: https://www.cornerstonejs.org/docs/
- **현재 DicomViewer 코드**: `sado_fe/src/features/dicom-viewer/components/DicomViewer.tsx`

---

## 완료 조건

- [ ] 모든 체크리스트 항목 완료
- [ ] TypeScript 컴파일 에러 0건
- [ ] npm run build 성공
- [ ] 테스트 시나리오 3개 모두 통과
- [ ] 4개 viewport 정상 렌더링
- [ ] 동기화/개별 재생 충돌 없음

---

**작성자**: Claude Code (Docs 터미널)
**전달 대상**: Frontend 터미널
**다음 단계**: 사용자가 이 파일 경로를 FE 터미널에 전달
