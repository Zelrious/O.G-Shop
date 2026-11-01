export interface BankOption {
  code: string;
  shortName: string;
  name: string;
  logo: string;
}

export const VIETNAMESE_BANKS: BankOption[] = [
  { code: 'VCB', shortName: 'Vietcombank', name: 'Ngoại Thương Việt Nam', logo: 'https://api.vietqr.io/img/VCB.png' },
  { code: 'MB', shortName: 'MB Bank', name: 'Quân Đội', logo: 'https://api.vietqr.io/img/MB.png' },
  { code: 'TCB', shortName: 'Techcombank', name: 'Kỹ Thương Việt Nam', logo: 'https://api.vietqr.io/img/TCB.png' },
  { code: 'BIDV', shortName: 'BIDV', name: 'Đầu tư & Phát triển Việt Nam', logo: 'https://api.vietqr.io/img/BIDV.png' },
  { code: 'CTG', shortName: 'VietinBank', name: 'Công Thương Việt Nam', logo: 'https://api.vietqr.io/img/ICB.png' },
  { code: 'VPB', shortName: 'VPBank', name: 'Việt Nam Thịnh Vượng', logo: 'https://api.vietqr.io/img/VPB.png' },
  { code: 'ACB', shortName: 'ACB', name: 'Á Châu', logo: 'https://api.vietqr.io/img/ACB.png' },
  { code: 'TPB', shortName: 'TPBank', name: 'Tiên Phong', logo: 'https://api.vietqr.io/img/TPB.png' },
  { code: 'STB', shortName: 'Sacombank', name: 'Sài Gòn Thương Tín', logo: 'https://api.vietqr.io/img/STB.png' },
  { code: 'VIB', shortName: 'VIB', name: 'Quốc Tế', logo: 'https://api.vietqr.io/img/VIB.png' },
  { code: 'VBA', shortName: 'Agribank', name: 'Nông nghiệp & PT Nông thôn VN', logo: 'https://api.vietqr.io/img/VBA.png' },
  { code: 'HDB', shortName: 'HDBank', name: 'Phát triển TP.HCM', logo: 'https://api.vietqr.io/img/HDB.png' },
  { code: 'MSB', shortName: 'MSB', name: 'Hàng Hải Việt Nam', logo: 'https://api.vietqr.io/img/MSB.png' },
  { code: 'OCB', shortName: 'OCB', name: 'Phương Đông', logo: 'https://api.vietqr.io/img/OCB.png' },
  { code: 'SHB', shortName: 'SHB', name: 'Sài Gòn - Hà Nội', logo: 'https://api.vietqr.io/img/SHB.png' },
  { code: 'LPB', shortName: 'LPBank', name: 'Lộc Phát Việt Nam', logo: 'https://api.vietqr.io/img/LPB.png' },
  { code: 'SSB', shortName: 'SeABank', name: 'Đông Nam Á', logo: 'https://api.vietqr.io/img/SEAB.png' },
  { code: 'BAB', shortName: 'Bac A Bank', name: 'Bắc Á', logo: 'https://api.vietqr.io/img/BAB.png' },
  { code: 'TIMO', shortName: 'Timo', name: 'Ngân hàng số Timo', logo: 'https://api.vietqr.io/img/TIMO.png' },
  { code: 'CAKE', shortName: 'Cake by VPBank', name: 'Ngân hàng số Cake', logo: 'https://api.vietqr.io/img/CAKE.png' },
];
