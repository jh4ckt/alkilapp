// Login del panel de administracion.
// Vive en un archivo propio (y no inline en login.html) para que la CSP del
// servidor pueda usar `script-src 'self'` sin 'unsafe-inline': con el script
// inline, el navegador lo bloqueaba y el panel era inaccesible.
(function () {
  const form = document.getElementById('loginForm');
  if (!form) return;
  const errorEl = document.getElementById('error');
  const userInput = document.getElementById('user');
  const passwordInput = document.getElementById('password');

  function showError(msg) {
    errorEl.textContent = msg;
    errorEl.classList.add('show');
    passwordInput.focus();
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    errorEl.classList.remove('show');
    const user = userInput.value.trim();
    const password = passwordInput.value.trim();
    if (!user) return showError('Ingresa el usuario de administrador');
    if (!password) return showError('Ingresa la contraseña');

    const btn = form.querySelector('button');
    btn.disabled = true;
    btn.textContent = 'Ingresando...';

    try {
      const res = await fetch('/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ user, password }),
        credentials: 'include'
      });
      if (res.ok || res.redirected) {
        window.location.href = '/';
      } else {
        await res.text();
        showError('Contraseña incorrecta');
      }
    } catch (err) {
      showError('Error de conexión. Intenta de nuevo.');
    } finally {
      btn.disabled = false;
      btn.textContent = 'Ingresar al panel';
    }
  });
})();
