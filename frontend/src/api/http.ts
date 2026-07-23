export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function parseErrorMessage(res: Response): Promise<string> {
  try {
    const data = await res.clone().json()
    return data?.error || data?.message || `요청 실패 (${res.status})`
  } catch {
    return `요청 실패 (${res.status})`
  }
}

export async function apiFetch<T>(input: string, init?: RequestInit): Promise<T> {
  const res = await fetch(input, init)
  if (!res.ok) {
    throw new ApiError(await parseErrorMessage(res), res.status)
  }
  if (res.status === 204) {
    return undefined as T
  }
  return (await res.json()) as T
}

export async function apiFetchVoid(input: string, init?: RequestInit): Promise<void> {
  const res = await fetch(input, init)
  if (!res.ok) {
    throw new ApiError(await parseErrorMessage(res), res.status)
  }
}
