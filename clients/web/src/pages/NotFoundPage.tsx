import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="flex h-full items-center justify-center bg-neutral-50">
      <div className="card p-10 text-center">
        <h1 className="text-2xl font-bold mb-2">페이지를 찾을 수 없어요</h1>
        <p className="text-sm text-neutral-500 mb-6">URL을 다시 확인해주세요.</p>
        <Link to="/plans" className="btn-primary">루틴으로 돌아가기</Link>
      </div>
    </div>
  );
}
