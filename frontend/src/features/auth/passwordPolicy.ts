export const NEW_PASSWORD_HINT = 'Mật khẩu phải có ít nhất 8 ký tự và không vượt quá 72 byte UTF-8.';

export function acceptsNewPassword(value: string): boolean {
  return value.trim().length > 0 && value.length >= 8 && new TextEncoder().encode(value).length <= 72;
}
