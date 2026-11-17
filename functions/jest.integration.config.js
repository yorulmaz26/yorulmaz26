/** Firebase Emulator Suite gerektiren entegrasyon testleri: npm run test:integration */
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  roots: ["<rootDir>/integration"],
  testMatch: ["**/*.int.test.ts"],
  testTimeout: 30000,
  maxWorkers: 1,
};
