document.querySelector('#get-location')?.addEventListener('click', () => {
  const status = document.querySelector('#location-status');
  if (!navigator.geolocation) { status.textContent = 'Location is unavailable. Enter coordinates or a landmark manually.'; return; }
  status.textContent = 'Finding your location…';
  navigator.geolocation.getCurrentPosition(position => {
    document.querySelector('#latitude').value = position.coords.latitude.toFixed(6);
    document.querySelector('#longitude').value = position.coords.longitude.toFixed(6);
    status.textContent = 'Location added. Please check that it matches the issue.';
  }, () => { status.textContent = 'Could not access location. Enter coordinates or a landmark manually.'; }, { enableHighAccuracy: true, timeout: 12000 });
});
let previewUrl;
document.querySelector('#photo')?.addEventListener('change', event => {
  const file = event.target.files[0];
  const preview = document.querySelector('#photo-preview');
  const status = document.querySelector('#photo-status');
  if (previewUrl) URL.revokeObjectURL(previewUrl);
  preview.hidden = true;
  status.textContent = '';
  if (!file) return;
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
    status.textContent = 'Please choose a JPEG or PNG photo smaller than 5 MB.';
    event.target.value = ''; return;
  }
  previewUrl = URL.createObjectURL(file);
  preview.src = previewUrl; preview.hidden = false;
  status.textContent = file.name;
});
