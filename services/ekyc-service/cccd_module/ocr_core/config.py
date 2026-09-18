from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

corner_detection = {
    'path_to_model': str(BASE_DIR / 'weights' / 'corner_detect.pt'),
    'nms_ths': 0.2,
    'score_ths': 0.15
}

text_detection = {
    'path_to_model': str(BASE_DIR / 'weights' / 'text_detect.pt'),
    'nms_ths': 0.2,
    'score_ths': 0.2
}

text_recognition = {
    'base_config': str(BASE_DIR / 'vietocr' / 'config_text_recognition' / 'base.yml'),
    'vgg_config': str(BASE_DIR / 'vietocr' / 'config_text_recognition' / 'vgg-transformer.yml'),
    'model_weight': str(BASE_DIR / 'vietocr' / 'config_text_recognition' / 'transformerocr.pth')
}
