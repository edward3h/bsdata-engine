// Runs the TeaVM build under Node: node run-engine.cjs <engine.js> <argument>
const engine = require(require('path').resolve(process.argv[2]));
engine.main([process.argv[3]]);
