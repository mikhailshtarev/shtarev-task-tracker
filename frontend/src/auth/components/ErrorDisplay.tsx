import type { ApiError } from '../../api/client';

// Отображение ошибок API (frontend-auth-integration.md, раздел 4.3).
export function ErrorDisplay({ error }: { error: ApiError | null }) {
  if (!error) {
    return null;
  }

  return (
    <div className="error-message" role="alert">
      {error.message}
      {error.details?.map((detail, i) => (
        <div key={i} className="error-detail">
          {detail.message}
        </div>
      ))}
    </div>
  );
}
