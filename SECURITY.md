# Política de Seguridad

**Yamy** se conecta directamente desde el dispositivo del usuario a Google Gemini utilizando la clave de API personal configurada por el propietario del dispositivo en **Ajustes > Cuenta de Google AI Studio**.

## Protección de Credenciales y Datos

- **Almacenamiento Cifrado**: La clave de API se guarda cifrada mediante **Android Keystore** (o **Keychain** en iOS) y jamás se exporta en las copias de seguridad de aprendizaje.
- **Datos Locales**: El historial de conversaciones, las palabras aprendidas, las barras de recuerdo (`RecallBars`) y las evaluaciones permanecen almacenadas localmente en el teléfono.
- **Prohibición de Secretos en el Código**: Queda estrictamente prohibido incluir archivos `.env`, claves privadas (`AIza...`, `.pem`, `.key`, `.jks`, `.keystore`), rutas locales absolutas o credenciales en el repositorio.

## Reportar una Vulnerabilidad

Si descubres un problema de seguridad (exposición de credenciales, acceso no autorizado a datos, importación insegura de copias de seguridad o fugas de información), repórtalo a través de la pestaña **Security > Report a vulnerability** o en el sistema de issues del repositorio oficial:

- **Repositorio oficial**: https://github.com/KelmerOC/Yamy

No publiques claves reales ni datos personales al reportar un fallo; utiliza ejemplos sintéticos y registros redactados.
