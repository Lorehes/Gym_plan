interface Props {
  title: string;
  description?: string;
  actions?: React.ReactNode;
}

export function PageHeader({ title, description, actions }: Props) {
  return (
    <header className="flex items-end justify-between gap-4 border-b border-neutral-200 px-4 py-4 md:px-8 md:py-6">
      <div>
        <h1 className="text-2xl font-bold text-neutral-900 md:text-3xl">{title}</h1>
        {description && (
          <p className="mt-1 text-sm text-neutral-500">{description}</p>
        )}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </header>
  );
}
