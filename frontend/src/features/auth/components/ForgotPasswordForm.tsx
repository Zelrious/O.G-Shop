import React from 'react';
import { Alert, Button } from '../../../shared/components';

interface ForgotPasswordFormProps {
  onSuccess?: () => void;
  onNavigateToLogin?: () => void;
}

export const ForgotPasswordForm: React.FC<ForgotPasswordFormProps> = ({ onNavigateToLogin }) => (
  <div>
    <Alert type="info">
      Khôi phục mật khẩu qua OTP sẽ được kích hoạt trong TASK-0006 sau khi adapter email được cấu hình.
      Hệ thống không còn sử dụng mã OTP hoặc mật khẩu giả lập trên trình duyệt.
    </Alert>
    <Button type="button" variant="outline" fullWidth onClick={onNavigateToLogin}>
      Quay lại Đăng nhập
    </Button>
  </div>
);
