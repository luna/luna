const fs = require("fs");

const path = "build.sbt";
const version = process.argv[2];
const edition = process.argv[3];

const content = fs.readFileSync(path, { encoding: "utf-8" });
const updated = content
  .replace(/val ensoVersion.*= ".*"/, 'val ensoVersion = "' + version + '"')
  .replace(
    /val currentEdition.*= ".*"/,
    'val currentEdition = "' + edition + '"'
  );
fs.writeFileSync(path, updated);

console.log("Updated build version to " + version);
console.log("Updated build edition to " + edition);
