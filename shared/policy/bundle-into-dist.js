'use strict';
const fs = require('fs');
const path = require('path');

const sharedDist = path.join(__dirname, 'dist');
const targetDir = path.join(process.cwd(), 'dist', 'node_modules', '@airsafe', 'policy');

fs.mkdirSync(path.join(targetDir, 'policy'), { recursive: true });
fs.writeFileSync(
  path.join(targetDir, 'package.json'),
  JSON.stringify({ name: '@airsafe/policy', version: '1.0.0', main: 'policy.js', types: 'policy.d.ts' }, null, 2)
);

for (const file of ['policy.js', 'policy.d.ts']) {
  const src = path.join(sharedDist, file);
  if (fs.existsSync(src)) {
    fs.copyFileSync(src, path.join(targetDir, file));
  }
}

for (const jsonFile of ['v1.2.json', 'v1.3.json']) {
  const src = path.join(sharedDist, 'policy', jsonFile);
  if (fs.existsSync(src)) {
    fs.copyFileSync(src, path.join(targetDir, 'policy', jsonFile));
  }
}
