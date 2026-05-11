# Later: Hugging Face Model Search

After the simple local LiteRT-LM chatbot works on device, add model discovery:

- Search Hugging Face for LiteRT-LM compatible repositories.
- Browse files for a selected repository.
- Show only `.litertlm` files as selectable model files.
- Convert selected Hugging Face `/blob/` URLs to `/resolve/` URLs for download.
- Show file size before download when available.
- Warn when filenames include hardware-specific hints such as `qualcomm_sm8750` or `qualcomm_gcs8275`.
- Recommend generic Android-compatible models unless the device hardware matches a specialized model.

This is intentionally out of v1 scope because v1 must first prove local download/import/delete and LiteRT-LM inference on the OPPO Reno11 5G.
