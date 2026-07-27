# Chinese Prediction Data Notice

The app-local Chinese handwriting prediction dictionary is generated from
`predict.txt` in the Rime `librime-predict` `data-1.0` release:

- Source:
  `https://github.com/rime/librime-predict/releases/download/data-1.0/predict.txt`
- Source SHA-256:
  `df0f7a9ef96569da402d9ea2376aefad4d15382ebcccb05ec84a0acbc00c7f83`
- Generated asset:
  `app/src/main/assets/t9/chinese-predict-v1.cpz`
- Generated asset SHA-256:
  `ef4b4b5ac5386e81304bc71a52531f68d37a39c72c371d9166fc1bbf55e84d3d`

The source data is distributed by Rime Developers under the BSD 3-Clause
License. The app's open-source license screen links the corresponding license
terms.

The generator creates a second Simplified Chinese section with OpenCC
conversion data. OpenCC is licensed under the Apache License 2.0 and is also
listed in the app's open-source license screen. The generated dictionary keeps
at most the ten ranked candidates supplied for each exact committed context.

Regenerate the asset with
`opencc-python-reimplemented==0.1.7`:

```shell
python3 scripts/generate_chinese_prediction_dictionary.py
```
