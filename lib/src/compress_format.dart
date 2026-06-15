enum CompressFormat {
  jpeg(['.jpg', '.jpeg']),
  png(['.png']),
  heic(['.heic', '.heif']),
  webp(['.webp']);

  const CompressFormat(this.suffixes);

  final List<String> suffixes;

  int get nativeValue => index;
}
