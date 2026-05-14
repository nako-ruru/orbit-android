pushd "%~dp0"
cd ..

wsl sh script/build-orbit.sh

popd
pause