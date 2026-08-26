const rows = document.querySelector('#peers');
const status = document.querySelector('#status');

async function load() {
  status.textContent = 'Loading…';
  try {
    const response = await fetch('/api/peers');
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const peers = await response.json();
    rows.replaceChildren(...peers.map(row));
    status.textContent = peers.length ? `${peers.length} peer${peers.length === 1 ? '' : 's'}` : 'No peers registered';
  } catch (error) {
    status.textContent = `Could not load peers: ${error.message}`;
  }
}

function row(peer) {
  const tr = document.createElement('tr');
  [peer.name, peer.address, peer.endpoint, peer.publicKey, new Date(peer.updatedAt).toLocaleString()].forEach((value, index) => {
    const td = document.createElement('td');
    if (index === 3) {
      const code = document.createElement('code');
      code.textContent = value;
      td.append(code);
    } else {
      td.textContent = value;
    }
    if (index === 4) {
      td.className = 'updated';
    }
    tr.append(td);
  });
  const action = document.createElement('td');
  const download = document.createElement('a');
  download.className = 'download';
  download.href = `/api/peers/${encodeURIComponent(peer.name)}/wg.conf`;
  download.textContent = 'Download';
  action.append(download);
  tr.append(action);
  return tr;
}

document.querySelector('#refresh').addEventListener('click', load);
load();
