module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  testMatch: ['**/__tests__/**/*.test.ts'],
  moduleNameMapper: {
    '^@airsafe/policy$': '<rootDir>/../../shared/policy/src/policy',
  },
};
