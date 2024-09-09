import React, { useState } from 'react';
import axios from 'axios';
import { Modal } from 'react-responsive-modal';
import 'react-responsive-modal/styles.css';
import appleHealthLogo from './apple-health-logo.png'; // Import Apple Health image
import './CreateProvider.css'; // Import custom styles

const CreateProvider = () => {
  const [formData, setFormData] = useState({
    age: '',
    gender: '',
    ethnicity: '',
    height: '',
    weight: '',
    sharingPreferences: {
      age: false,
      gender: false,
      ethnicity: false,
      height: false,
      weight: false,
      steps: false,
      sleep_hours: false,
      calories_consumed: false,
      exercise_minutes: false,
      calories_expended: false,
      resting_heart_rate: false,
      heart_rate: false,
      blood_pressure: false,
    },
  });

  const [appleHealthConnected, setAppleHealthConnected] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const handleChange = (e) => {
    const { name, value, checked, type } = e.target;
    if (type === 'checkbox') {
      setFormData({
        ...formData,
        sharingPreferences: {
          ...formData.sharingPreferences,
          [name]: checked,
        },
      });
    } else {
      setFormData({
        ...formData,
        [name]: value,
      });
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      await axios.post('http://localhost:3000/create-data-provider', formData);
      setSubmitted(true);
    } catch (error) {
      console.error(error);
    }
    setLoading(false);
  };

  const connectAppleHealth = () => {
    setAppleHealthConnected(false); // Initially hide the table
    setModalOpen(false); // Close the modal immediately after clicking connect
    setLoading(true); // Show spinner
    setTimeout(() => {
      setAppleHealthConnected(true); // Show the Apple Health table after the delay
      setLoading(false);
    }, 2000); // Simulate loading time after closing modal
  };

  return (
    <div className="create-provider-container">
      <h2>Create Data Provider Profile</h2>
      <form onSubmit={handleSubmit} className="provider-form">
        <div className="grid-header">
          <span></span> {/* Empty column for alignment */}
        </div>
        <div className="demographic-section">
          {/* Grid Layout for Demographic Data */}

          <div className="grid-row">
            <label className="bold-label">Age:</label>
            <input
              type="number"
              name="age"
              value={formData.age}
              onChange={handleChange}
            />
            <input
              type="checkbox"
              name="age"
              className="checkbox"
              disabled={!formData.age}
              checked={formData.sharingPreferences.age}
              onChange={handleChange}
            />
          </div>

          <div className="grid-row">
            <label className="bold-label">Gender:</label>
            <select name="gender" value={formData.gender} onChange={handleChange}>
              <option value="">Select...</option>
              <option value="male">Male</option>
              <option value="female">Female</option>
              <option value="nonbinary">Nonbinary</option>
            </select>
            <input
              type="checkbox"
              name="gender"
              className="checkbox"
              disabled={!formData.gender}
              checked={formData.sharingPreferences.gender}
              onChange={handleChange}
            />
          </div>

          <div className="grid-row">
            <label className="bold-label">Ethnicity:</label>
            <input
              type="text"
              name="ethnicity"
              value={formData.ethnicity}
              onChange={handleChange}
            />
            <input
              type="checkbox"
              name="ethnicity"
              className="checkbox"
              disabled={!formData.ethnicity}
              checked={formData.sharingPreferences.ethnicity}
              onChange={handleChange}
            />
          </div>

          <div className="grid-row">
            <label className="bold-label">Height (cm):</label>
            <input
              type="number"
              name="height"
              value={formData.height}
              onChange={handleChange}
            />
            <input
              type="checkbox"
              name="height"
              className="checkbox"
              disabled={!formData.height}
              checked={formData.sharingPreferences.height}
              onChange={handleChange}
            />
          </div>

          <div className="grid-row">
            <label className="bold-label">Weight (lbs):</label>
            <input
              type="number"
              name="weight"
              value={formData.weight}
              onChange={handleChange}
            />
            <input
              type="checkbox"
              name="weight"
              className="checkbox"
              disabled={!formData.weight}
              checked={formData.sharingPreferences.weight}
              onChange={handleChange}
            />
          </div>
        </div>

        {!appleHealthConnected && (
          <button type="button" className="connect-health-btn" onClick={() => setModalOpen(true)}>
            <img src={appleHealthLogo} alt="Apple Health" />
            Connect Apple Health
          </button>
        )}

        {loading && (
          <div className="spinner"></div>
        )}

        {appleHealthConnected && !loading && (
          <div className="health-data-section">
            <h3>Apple Health Data</h3>
            {/* Table for Apple Health Data */}
            <div className="health-data-table">
              <div className="grid-row bold-label">
                <span>Data</span>
                <span>Frequency</span>
                <span>Description</span>
                <span>Share</span>
              </div>
              {[
                { name: 'Sleep Hours', frequency: 'Daily', desc: 'Total sleep hours for the day' },
                { name: 'Resting Heart Rate', frequency: 'Daily', desc: 'Resting heart rate for the day' },
                { name: 'Blood Pressure', frequency: 'Daily', desc: 'Average daily blood pressure' },
                { name: 'Steps', frequency: 'Hourly', desc: 'Steps in the last hour' },
                { name: 'Calories Consumed', frequency: 'Hourly', desc: 'Calories consumed in the last hour' },
                { name: 'Exercise Minutes', frequency: 'Hourly', desc: 'Exercise in the last hour' },
                { name: 'Heart Rate', frequency: 'Hourly', desc: 'Average heart rate in the last hour' },
                { name: 'Calories Expended', frequency: 'Hourly', desc: 'Calories burned in the last hour' },
              ].map((item) => (
                <div key={item.name} className="grid-row">
                  <span className="bold-label">{item.name}</span>
                  <span>{item.frequency}</span>
                  <span>{item.desc}</span>
                  <input
                    type="checkbox"
                    name={item.name.replace(' ', '_').toLowerCase()}
                    checked={formData.sharingPreferences[item.name.replace(' ', '_').toLowerCase()]}
                    onChange={handleChange}
                  />
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="submit-section">
          {loading ? (
            <div className="spinner"></div>
          ) : submitted ? (
            <div className="success-check">✔️ Profile Created</div>
          ) : (
            <button type="submit" className="create-profile-btn">Create Profile</button>
          )}
        </div>
      </form>

      {/* Modal for Apple Health Connection */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        center
        classNames={{
          modal: 'customModal',
        }}
      >
        <h3>Authorize Apple Health</h3>
        <p>This app wants to access the following data:</p>
        <ul className="modal-list">
          <li>Steps</li>
          <li>Calories Consumed</li>
          <li>Sleep Hours</li>
          <li>Heart Rate</li>
          <li>Resting Heart Rate</li>
          <li>Blood Pressure</li>
          <li>Exercise Minutes</li>
          <li>Calories Expended</li>
        </ul>
        <button className="connect-health-btn" onClick={connectAppleHealth}>
          <img src={appleHealthLogo} alt="Apple Health" />
          Connect
        </button>
      </Modal>
    </div>
  );
};

export default CreateProvider;
