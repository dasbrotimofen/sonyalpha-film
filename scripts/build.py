"""Repository-local Sony PMCA build. Requires Python 3.12+; Windows/Linux x64."""
import argparse
import hashlib
import json
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEPS = ROOT / '.deps'
HOST = 'windows' if sys.platform == 'win32' else 'linux'
JDK = DEPS / 'jdk'
BT = DEPS / 'android-sdk/build-tools/36.0.0'
ANDROID = DEPS / 'android-sdk/platforms/android-10/android.jar'
STUBS = DEPS / 'sony-stubs.jar'
LOCK = json.loads((ROOT / 'scripts/dependencies.json').read_text())


def run(*args, cwd=ROOT):
    subprocess.run([str(a) for a in args], cwd=cwd, check=True)


def java_tool(name):
    return JDK / 'bin' / (name + ('.exe' if HOST == 'windows' else ''))


def android_tool(name):
    return BT / (name + ('.exe' if HOST == 'windows' else ''))


def download(name):
    spec = LOCK[name]
    dest = DEPS / 'downloads' / name
    dest.parent.mkdir(parents=True, exist_ok=True)
    if not dest.exists():
        print('Downloading ' + name, flush=True)
        partial = dest.with_suffix('.partial')
        with urllib.request.urlopen(spec['url'], timeout=120) as src, partial.open('wb') as out:
            shutil.copyfileobj(src, out)
        partial.replace(dest)
    digest = hashlib.new(spec['algorithm'])
    with dest.open('rb') as src:
        for chunk in iter(lambda: src.read(1024 * 1024), b''):
            digest.update(chunk)
    if digest.hexdigest() != spec['checksum']:
        raise RuntimeError('Checksum mismatch; remove and retry: ' + str(dest))
    return dest


def unpack(name, dest):
    if dest.exists():
        return
    archive = download(name)
    with tempfile.TemporaryDirectory(dir=DEPS) as temp:
        target = Path(temp)
        if zipfile.is_zipfile(archive):
            with zipfile.ZipFile(archive) as z:
                for item in z.infolist():
                    if not (target / item.filename).resolve().is_relative_to(target.resolve()):
                        raise RuntimeError('Unsafe archive path')
                z.extractall(target)
                if HOST != 'windows':
                    for item in z.infolist():
                        mode = item.external_attr >> 16
                        if mode:
                            (target / item.filename).chmod(mode)
        else:
            with tarfile.open(archive) as t:
                t.extractall(target, filter='data')
        children = list(target.iterdir())
        if len(children) != 1 or not children[0].is_dir():
            raise RuntimeError('Expected one top-level archive directory: ' + name)
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(children[0]), str(dest))


def setup():
    if sys.platform not in ('win32', 'linux') or platform.machine().lower() not in ('amd64', 'x86_64'):
        raise RuntimeError('Automatic setup supports Windows and Linux x64.')
    DEPS.mkdir(exist_ok=True)
    unpack('jdk-' + HOST, JDK)
    unpack('android', ANDROID.parent)
    unpack('build-tools-' + HOST, BT)
    unpack('sony', DEPS / 'OpenMemories-Framework')
    unpack('jasmin', DEPS / 'jasmin')
    assembler = DEPS / 'jasmin/jasmin.jar'
    if not STUBS.exists():
        source = DEPS / 'OpenMemories-Framework/stubs/src/main/jasmin'
        with tempfile.TemporaryDirectory(dir=DEPS) as temp:
            classes = Path(temp)
            files = sorted(source.rglob('*.j'))
            for start in range(0, len(files), 40):
                run(java_tool('java'), '-jar', assembler, '-d', classes,
                    *[p.relative_to(source) for p in files[start:start + 40]], cwd=source)
            if not files or len(list(classes.rglob('*.class'))) != len(files):
                raise RuntimeError('Sony stub assembly did not produce every class')
            run(java_tool('jar'), 'cf', STUBS, '-C', classes, '.')
    run(java_tool('java'), '-version')
    print('Toolchain ready in ' + str(DEPS), flush=True)


def clean_build(module):
    folder = ROOT / module / 'build'
    # Never recursively remove a path outside the intended module directory.
    if folder.resolve() != ROOT.resolve() / module / 'build' or folder.is_symlink():
        raise RuntimeError('Unsafe build directory: ' + str(folder))
    if folder.exists():
        shutil.rmtree(folder)
    folder.mkdir(parents=True)
    return folder


def argfile(path, files):
    path.write_text('\n'.join('"' + p.as_posix() + '"' for p in files) + '\n', encoding='utf-8')
    return '@' + str(path)


def build_tools():
    out = clean_build('tools')
    sources = sorted((ROOT / 'tools/src').rglob('*.java'))
    sources += sorted((ROOT / 'tools/test').rglob('*.java'))
    # DevelopTest exercises the actual camera image-processing implementation.
    sources.append(ROOT / 'app/src/com/filmsim/app/Develop.java')
    sources.append(ROOT / 'app/src/com/filmsim/app/FilmLook.java')
    sources.append(ROOT / 'app/src/com/filmsim/app/PhotoFiles.java')
    sources.append(ROOT / 'app/src/com/filmsim/app/CopyExif.java')
    sources.append(ROOT / 'app/src/com/filmsim/app/ShootingControls.java')
    run(java_tool('javac'), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
        '-Xlint:-options', '-d', out, argfile(out / 'sources.txt', sources))
    print('Built desktop tools', flush=True)
    run(java_tool('java'), '-cp', out, 'com.filmsim.tools.DevelopAlphaTest')
    run(java_tool('java'), '-cp', out, 'com.filmsim.tools.PhotoFilesTest')
    run(java_tool('java'), '-cp', out, 'com.filmsim.app.PhotoFilesListingTest')
    run(java_tool('java'), '-cp', out, 'com.filmsim.app.PhotoFilesPublicationTest')
    run(java_tool('java'), '-cp', out, 'com.filmsim.tools.CopyExifTest')
    run(java_tool('java'), '-cp', out, 'com.filmsim.tools.ShootingControlsTest')


def build_app(module):
    base = ROOT / module
    out = clean_build(module)
    for name in ('gen', 'classes', 'dex'):
        (out / name).mkdir()
    keystore = base / 'debug.keystore'
    if not keystore.exists():
        run(java_tool('keytool'), '-genkeypair', '-alias', 'debug', '-keyalg', 'RSA',
            '-keysize', '2048', '-validity', '10000', '-keystore', keystore,
            '-storepass', 'android', '-keypass', 'android', '-dname', 'CN=FilmSim Debug')
    resource_args = ['-M', base / 'AndroidManifest.xml', '-S', base / 'res', '-I', ANDROID]
    run(android_tool('aapt'), 'package', '-f', '-m', '-J', out / 'gen', *resource_args)
    sources = sorted((base / 'src').rglob('*.java')) + sorted((out / 'gen').rglob('*.java'))
    run(java_tool('javac'), '-source', '8', '-target', '8', '-Xlint:-options', '-nowarn',
        '-encoding', 'UTF-8', '-bootclasspath', ANDROID, '-cp', STUBS,
        '-d', out / 'classes', argfile(out / 'sources.txt', sources))
    # A jar avoids Windows command-line length limits when passing classes to D8.
    run(java_tool('jar'), 'cf', out / 'classes.jar', '-C', out / 'classes', '.')
    run(java_tool('java'), '-cp', BT / 'lib/d8.jar', 'com.android.tools.r8.D8',
        '--min-api', '10', '--release', '--lib', ANDROID, '--classpath', STUBS,
        '--output', out / 'dex', out / 'classes.jar')
    run(android_tool('aapt'), 'package', '-f', *resource_args, '-A', base / 'assets',
        '-F', out / 'unsigned.apk')
    with zipfile.ZipFile(out / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
        z.write(out / 'dex/classes.dex', 'classes.dex')
    run(android_tool('zipalign'), '-f', '4', out / 'unsigned.apk', out / 'aligned.apk')
    apk = base / ('FilmSim.apk' if module == 'app' else 'FilmProbe.apk')
    signer = [java_tool('java'), '-jar', BT / 'lib/apksigner.jar']
    run(*signer, 'sign', '--ks', keystore, '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
        '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'false',
        '--v3-signing-enabled', 'false', '--v4-signing-enabled', 'false',
        '--min-sdk-version', '10', '--out', apk, out / 'aligned.apk')
    run(*signer, 'verify', '--verbose', '--min-sdk-version', '10', apk)
    run(android_tool('zipalign'), '-c', '4', apk)
    print('Built and verified ' + str(apk), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('target', choices=['setup', 'all', 'app', 'probe', 'tools'], nargs='?', default='all')
    args = parser.parse_args()
    if args.target == 'setup':
        setup()
        return
    if not all(p.exists() for p in (java_tool('javac'), ANDROID, STUBS, BT)):
        parser.error('Toolchain missing. Run: python scripts/build.py setup')
    if args.target in ('all', 'tools'):
        build_tools()
    for module in ('app', 'probe'):
        if args.target in ('all', module):
            build_app(module)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
