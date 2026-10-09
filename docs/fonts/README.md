# Bundled interface fonts

- Geist: https://github.com/google/fonts/tree/main/ofl/geist
- Fraunces: https://github.com/google/fonts/tree/main/ofl/fraunces

Both are licensed under the SIL Open Font License 1.1 (the complete licences are beside this file). Sources were retrieved on 2026-10-08. Static TTF faces are bundled, with the original character coverage including Latin Extended, for Android API 26 and later. Geist has 400/500/600/700 faces; Fraunces has upright and italic 500 faces, optical size 32, with the other axes at their upstream defaults. The static faces were generated using fontTools `instantiateVariableFont` with all axes pinned. No font or rendering dependency is required at runtime.
