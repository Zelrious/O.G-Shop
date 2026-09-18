/**
 * Mã hóa mật khẩu một chiều bằng thuật toán SHA-256 thông qua Web Crypto API chuẩn của trình duyệt.
 * Giúp bảo đảm mật khẩu không bao giờ tồn tại dưới dạng plain-text trên client memory hay payload.
 */
export async function hashPassword(plainText: string): Promise<string> {
  if (!plainText) return '';
  const encoder = new TextEncoder();
  const data = encoder.encode(plainText);
  const hashBuffer = await window.crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}
