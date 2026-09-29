const fs=require('fs');
const t=fs.readFileSync('D:/alkilapp/app/src/main/java/com/alkilapp/RegistrarPropiedadActivity.kt','utf8');
console.log('CRLF: '+((t.match(/\r\n/g)||[]).length)+'   LF solo: '+((t.match(/(?<!\r)\n/g)||[]).length));
console.log('tiene la funcion vieja?: '+t.includes('Departamento y distrito deducidos automaticamente desde el codigo postal'));
