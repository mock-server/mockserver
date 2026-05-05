import mockserverNode from '../';

async function beforeAll() {
  await mockserverNode.start_mockserver({
    serverPort: 1080,
    verbose: true
  });
}

async function afterAll() {
  await mockserverNode.stop_mockserver({
    serverPort: 1080
  });
}
