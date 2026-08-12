import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ProfileModal from './ProfileModal'
import { saveProfile, saveProfileFile, deleteProfileFile } from '../api'

vi.mock('../api', () => ({
  saveProfile: vi.fn(),
  saveProfileFile: vi.fn(),
  deleteProfileFile: vi.fn(),
}))

describe('ProfileModal', () => {
  beforeEach(() => {
    vi.mocked(saveProfile).mockReset()
    vi.mocked(saveProfileFile).mockReset()
    vi.mocked(deleteProfileFile).mockReset()
  })

  it('open이 false면 아무것도 렌더링하지 않는다', () => {
    const { container } = render(
      <ProfileModal
        open={false}
        onClose={vi.fn()}
        initialProfileText=""
        initialFiles={[]}
        onProfileSaved={vi.fn()}
        onFilesChanged={vi.fn()}
      />,
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('텍스트를 입력하고 저장하면 saveProfile을 호출하고 성공 메시지를 보여준다', async () => {
    vi.mocked(saveProfile).mockResolvedValue({ profileText: '경력 5년' })
    const user = userEvent.setup()
    const onProfileSaved = vi.fn()

    render(
      <ProfileModal
        open
        onClose={vi.fn()}
        initialProfileText=""
        initialFiles={[]}
        onProfileSaved={onProfileSaved}
        onFilesChanged={vi.fn()}
      />,
    )

    await user.type(screen.getByRole('textbox'), '경력 5년')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(saveProfile).toHaveBeenCalledWith('경력 5년')
    expect(await screen.findByText('이력서가 저장되었어요')).toBeInTheDocument()
    expect(onProfileSaved).toHaveBeenCalledWith('경력 5년')
  })

  it('저장 실패 시 에러 메시지를 보여준다', async () => {
    vi.mocked(saveProfile).mockRejectedValue(new Error('저장할 수 없어요'))
    const user = userEvent.setup()

    render(
      <ProfileModal
        open
        onClose={vi.fn()}
        initialProfileText=""
        initialFiles={[]}
        onProfileSaved={vi.fn()}
        onFilesChanged={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText('저장할 수 없어요')).toBeInTheDocument()
  })

  it('파일 업로드 탭에서 저장된 파일 목록을 보여주고 개별 삭제할 수 있다', async () => {
    vi.mocked(deleteProfileFile).mockResolvedValue(undefined)
    const user = userEvent.setup()
    const onFilesChanged = vi.fn()

    render(
      <ProfileModal
        open
        onClose={vi.fn()}
        initialProfileText=""
        initialFiles={[
          { id: 1, fileName: '이력서.pdf', fileType: 'application/pdf' },
          { id: 2, fileName: '포트폴리오.pptx', fileType: 'application/vnd.ms-powerpoint' },
        ]}
        onProfileSaved={vi.fn()}
        onFilesChanged={onFilesChanged}
      />,
    )

    await user.click(screen.getByRole('button', { name: '파일 업로드' }))

    expect(screen.getByText('이력서.pdf')).toBeInTheDocument()
    expect(screen.getByText('포트폴리오.pptx')).toBeInTheDocument()

    const rows = screen.getAllByRole('button', { name: '삭제' })
    await user.click(rows[0])

    expect(deleteProfileFile).toHaveBeenCalledWith(1)
    expect(screen.queryByText('이력서.pdf')).not.toBeInTheDocument()
    await vi.waitFor(() =>
      expect(onFilesChanged).toHaveBeenCalledWith([{ id: 2, fileName: '포트폴리오.pptx', fileType: 'application/vnd.ms-powerpoint' }]),
    )
  })

  it('저장된 파일이 없으면 안내 문구를 보여준다', async () => {
    const user = userEvent.setup()
    render(
      <ProfileModal
        open
        onClose={vi.fn()}
        initialProfileText=""
        initialFiles={[]}
        onProfileSaved={vi.fn()}
        onFilesChanged={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: '파일 업로드' }))

    expect(screen.getByText('저장된 파일이 없어요')).toBeInTheDocument()
  })

  it('파일을 선택하고 업로드하면 saveProfileFile을 호출하고 목록에 추가한다', async () => {
    vi.mocked(saveProfileFile).mockResolvedValue({ id: 3, fileName: '새이력서.pdf', fileType: 'application/pdf' })
    const user = userEvent.setup()
    const onFilesChanged = vi.fn()

    const { container } = render(
      <ProfileModal
        open
        onClose={vi.fn()}
        initialProfileText=""
        initialFiles={[]}
        onProfileSaved={vi.fn()}
        onFilesChanged={onFilesChanged}
      />,
    )

    await user.click(screen.getByRole('button', { name: '파일 업로드' }))

    const file = new File(['dummy'], '새이력서.pdf', { type: 'application/pdf' })
    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, file)

    expect(screen.getByText('새이력서.pdf')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /업로드\/저장/ }))

    expect(saveProfileFile).toHaveBeenCalledWith(file)
    expect(await screen.findByText('파일이 저장되었어요 (1/3)')).toBeInTheDocument()
    expect(onFilesChanged).toHaveBeenCalledWith([{ id: 3, fileName: '새이력서.pdf', fileType: 'application/pdf' }])
  })

  it('파일이 3개면 업로드 버튼이 비활성화된다', async () => {
    const user = userEvent.setup()
    render(
      <ProfileModal
        open
        onClose={vi.fn()}
        initialProfileText=""
        initialFiles={[
          { id: 1, fileName: 'a.pdf', fileType: 'application/pdf' },
          { id: 2, fileName: 'b.pdf', fileType: 'application/pdf' },
          { id: 3, fileName: 'c.pdf', fileType: 'application/pdf' },
        ]}
        onProfileSaved={vi.fn()}
        onFilesChanged={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: '파일 업로드' }))

    expect(screen.getByRole('button', { name: /업로드\/저장/ })).toBeDisabled()
  })

  it('저장 중에는 오버레이 클릭으로 닫히지 않는다', async () => {
    let resolveSave: (value: { profileText: string }) => void
    const savePromise = new Promise<{ profileText: string }>((resolve) => {
      resolveSave = resolve
    })
    vi.mocked(saveProfile).mockReturnValue(savePromise)
    const user = userEvent.setup()
    const onClose = vi.fn()

    const { container } = render(
      <ProfileModal
        open
        onClose={onClose}
        initialProfileText="내용"
        initialFiles={[]}
        onProfileSaved={vi.fn()}
        onFilesChanged={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: '저장' }))
    expect(screen.getByRole('button', { name: '저장 중...' })).toBeDisabled()

    const overlay = container.querySelector('.modal-overlay') as HTMLElement
    await user.click(overlay)
    expect(onClose).not.toHaveBeenCalled()

    resolveSave!({ profileText: '내용' })
  })
})
